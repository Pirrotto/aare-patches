package app.template.patches.aare

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.shared.Constants.COMPATIBILITY_AARE

private const val MAP_AREA_FIX = "Lapp/template/extension/aare/MapAreaFix;"
private const val MAP_BOX = "Lnet/prototux/aare/host/templates/MapGeometry\$Box;"

@Suppress("unused")
val mapAreaFixPatch = patch(
    name = "Map centred right",
    description = "The navigation app gets the largest free part of the map, so the route doesn't slide towards the cards. Off by default.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    dependsOn(settingsPatch)

    execute {
        registerSetting("map_area_fix")

        // At the start, before AARE's card-by-card cut: our rectangle, or AARE's own when null.
        MapUnobstructedFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1, p2 }, $MAP_AREA_FIX->unobstructed(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :aare_map_area
                    check-cast v0, $MAP_BOX
                    return-object v0
                """,
                ExternalLabel("aare_map_area", getInstruction(0)),
            )
        }
    }
}
