package app.template.patches.aare

import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.template.patches.shared.Constants.COMPATIBILITY_AARE
import app.template.patches.shared.addManifestPermission

private const val SERVICE = "app.template.extension.aare.adb.IntegratedShizukuService"
private const val BOOT_RECEIVER = "app.template.extension.aare.adb.BootReceiver"

@Suppress("unused")
val integratedShizukuPatch = patch(
    name = "Integrated Shizuku",
    description = "AARE starts its own Shizuku through wireless debugging, without the Shizuku app: pair once " +
        "from Settings, and AARE starts it again by itself after each restart, on Wi-Fi.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    dependsOn(settingsPatch)

    execute {
        registerSetting("integrated_shizuku")

        // Granted by AARE's own shell at the first start: lets AARE switch wireless debugging on
        // for the few seconds a start takes, also after a restart.
        addManifestPermission("android.permission.WRITE_SECURE_SETTINGS")

        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.root.children("application").first()
            fun declared(tag: String, name: String) =
                application.children(tag).any { it.attribute("android:name") == name }

            if (!declared("service", SERVICE)) {
                application.appendChild("service") {
                    setAttribute("android:name", SERVICE)
                    setAttribute("android:exported", "false")
                    setAttribute("android:foregroundServiceType", "connectedDevice")
                }
            }
            if (!declared("receiver", BOOT_RECEIVER)) {
                application.appendChild("receiver") {
                    setAttribute("android:name", BOOT_RECEIVER)
                    setAttribute("android:exported", "false")
                    appendChild("intent-filter") {
                        appendChild("action") {
                            setAttribute("android:name", "android.intent.action.BOOT_COMPLETED")
                        }
                    }
                }
            }
        }
    }
}
