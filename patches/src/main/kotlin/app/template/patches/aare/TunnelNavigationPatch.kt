package app.template.patches.aare

import app.morphe.patcher.Fingerprint
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

@Suppress("unused")
val tunnelNavigationPatch = patch(
    name = "Tunnel navigation",
    description = "With the car's GPS used for the phone, the position keeps moving in tunnels: it is estimated from the " +
        "car's speed, compass and gyroscope, with an honest accuracy, and stops before that passes 45 m (about 400 m " +
        "of tunnel), 5 km or 5 minutes. Outside tunnels it learns " +
        "from the GPS how much to turn in curves, and remembers it for each car model (deleted when switched off). " +
        "Off by default: turn it on in the patches' Settings. A test switch below it, \"Location check\", notes in the " +
        "diagnostics whether the navigation apps may read the location (with Shizuku); the test switch \"Location for " +
        "navigation at the start\" has the Shizuku shell start a small location service in AARE, so the navigation app " +
        "gets the location without opening AARE on the phone.",
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
    }
}
