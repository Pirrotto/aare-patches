package app.template.patches.aare

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_AARE

private const val MAP_GEOMETRY = "Lnet/prototux/aare/host/templates/MapGeometry;"
private const val BOX = "Lnet/prototux/aare/host/templates/MapGeometry\$Box;"
private const val MAP_AREA_NOTE = "Lapp/template/extension/aare/MapAreaNote;"

/** MapGeometry.unobstructed(map, obstacles): the free area of the map told to the navigation app. */
internal object MapUnobstructedFingerprint : Fingerprint(
    definingClass = MAP_GEOMETRY,
    name = "unobstructed",
    returnType = BOX,
    parameters = listOf(BOX, "Ljava/util/List;"),
)

@Suppress("unused")
val mapAreaNotePatch = patch(
    name = "Map area check",
    description = "Test diagnostics: notes in pixels the free area of the map AARE gives the navigation app, for the map that moves aside. Off by default.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    dependsOn(settingsPatch)

    execute {
        registerSetting("map_area_note")

        // At the start the parameters are still in their registers.
        MapUnobstructedFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1, p2 }, $MAP_AREA_NOTE->note(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
