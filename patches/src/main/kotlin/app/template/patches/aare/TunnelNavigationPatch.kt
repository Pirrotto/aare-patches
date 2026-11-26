package app.template.patches.aare

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.shared.Constants.COMPATIBILITY_AARE

/** PhoneLocationFeed.push(Location): gives the phone the car's GPS fix. */
internal object PhoneLocationFeedPushFingerprint : Fingerprint(
    definingClass = "Lnet/prototux/aare/car/PhoneLocationFeed;",
    name = "push",
    returnType = "V",
    parameters = listOf("Landroid/location/Location;"),
)

/** NavStatusBridge.update(Trip): the hosted navigation app's trip, on its way to the car's cluster. */
internal object NavStatusBridgeUpdateFingerprint : Fingerprint(
    definingClass = "Lnet/prototux/aare/car/NavStatusBridge;",
    name = "update",
    returnType = "V",
    parameters = listOf("Landroidx/car/app/navigation/model/Trip;"),
)

@Suppress("unused")
val tunnelNavigationPatch = patch(
    name = "Tunnel navigation",
    description = "With the car's GPS used for the phone, the position keeps moving in tunnels: it is estimated from the " +
        "car's speed, compass and gyroscope, with an honest accuracy, and stops before that passes 45 m (about 400 m " +
        "of tunnel at first, more once the tunnels' exits measured the car's own error), 5 km or 5 minutes. Outside tunnels it learns " +
        "from the GPS how much to turn in curves, and remembers it for each car model (deleted when switched off). " +
        "Off by default: turn it on in the patches' Settings. A test switch below it, \"Location check\", notes in the " +
        "diagnostics whether the navigation apps may read the location (with Shizuku); the test switch \"Location for " +
        "navigation at the start\" has the Shizuku shell start a small location service in AARE, so the navigation app " +
        "gets the location without opening AARE on the phone. \"Tunnel maps\" in the patches' Settings downloads, on a " +
        "tap, the road tunnels of the chosen countries (OpenStreetMap, signed, from this project's GitHub page; " +
        "optional daily update and \"Add the country I'm in\", both off by default); in those tunnels the estimate " +
        "follows the tunnel's shape; at a junction it takes the branch of the navigation's next maneuver (or straight " +
        "on without one), and goes back to the gyroscope where it isn't sure.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    dependsOn(settingsPatch)

    execute {
        registerSetting("tunnel_navigation")

        // The bridge for the test switch "Location for navigation at the start": started only by the shell
        // (Shizuku helper) or the system, which alone hold INTERACT_ACROSS_USERS_FULL, as AARE's Shizuku provider.
        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.root.children("application").first()
            val bridge = "app.template.extension.aare.LocationBridgeService"
            if (application.children("service").none { it.attribute("android:name") == bridge }) {
                application.appendChild("service") {
                    setAttribute("android:name", bridge)
                    setAttribute("android:exported", "true")
                    setAttribute("android:permission", "android.permission.INTERACT_ACROSS_USERS_FULL")
                    setAttribute("android:foregroundServiceType", "location")
                }
            }
        }

        PhoneLocationFeedPushFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, Lapp/template/extension/aare/TunnelNavigation;->onPush(Ljava/lang/Object;Landroid/location/Location;)Z
                    move-result v0
                    if-eqz v0, :aare_tunnel
                    return-void
                """,
                ExternalLabel("aare_tunnel", getInstruction(0)),
            )
        }

        // The next maneuver, read for the branch to take where a tunnel of the maps splits.
        NavStatusBridgeUpdateFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, Lapp/template/extension/aare/TunnelManeuver;->onTrip(Ljava/lang/Object;)V",
        )
    }
}
