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
    description = "In tunnels the position keeps moving, estimated from the car's speed and gyroscope with an honest accuracy; with the optional tunnel maps it follows the tunnel and its junctions. Off by default.",
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
