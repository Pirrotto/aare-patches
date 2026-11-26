package app.template.patches.aare

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_AARE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ASSISTANT_CLASS = "Lapp/template/extension/aare/AssistantCarScreen;"
private const val CAR_RUNTIME = "Lnet/prototux/aare/host/CarRuntime;"
private const val CONNECTION_CLASS = "Lnet/prototux/aare/host/CarAppConnection;"
private const val UI_STATE = "Lnet/prototux/aare/ui/car/CarUiState;"

/** CarRuntime.assistant(): the steering-wheel voice button and the rail's mic. */
internal object CarRuntimeAssistantFingerprint : Fingerprint(
    definingClass = CAR_RUNTIME,
    name = "assistant",
    returnType = "V",
    parameters = listOf(),
)

/** CarRuntime.applyScreenSettings(): where the car-screen assistant is closed when driving starts. */
internal object CarRuntimeApplyScreenSettingsFingerprint : Fingerprint(
    definingClass = CAR_RUNTIME,
    name = "applyScreenSettings",
    returnType = "V",
    parameters = listOf(),
)

/** CarAppConnection.fail(message): AARE gives up on a car app (no answer in 10 s, or no bind). */
internal object CarAppConnectionFailFingerprint : Fingerprint(
    definingClass = CONNECTION_CLASS,
    name = "fail",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

private fun Instruction.calls(definingClass: String, name: String): Boolean {
    val ref = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return ref.definingClass == definingClass && ref.name == name
}

/** `invoke-static {vN}` for any register: the plain form only reaches v0–v15. */
private fun invokeStatic(register: Int, method: String) =
    if (register < 16) "invoke-static { v$register }, $method" else "invoke-static/range { v$register .. v$register }, $method"

/** The register the move-result right after the call at [index] writes, or an error naming [what]. */
private fun resultRegister(instructions: List<Instruction>, index: Int, what: String): Int {
    val next = instructions.getOrNull(index + 1)
    if (next == null || (next.opcode != Opcode.MOVE_RESULT && next.opcode != Opcode.MOVE_RESULT_OBJECT)) {
        throw PatchException("$what: no move-result after the call")
    }
    return (next as OneRegisterInstruction).registerA
}

/** Passes a getDrivingRestricted() result in [register] through AssistantCarScreen.drivingRestricted. */
private fun drivingFilter(register: Int) = """
    ${invokeStatic(register, "$ASSISTANT_CLASS->drivingRestricted(Z)Z")}
    move-result v$register
"""

@Suppress("unused")
val assistantCarScreenPatch = patch(
    name = "Assistant on the car screen",
    description = "When an assistant's car app doesn't answer AARE (Google's), the voice button opens it as an assistant without a car app: on the car screen with AARE's option, else on the phone. Optionally also while driving. Off by default.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    dependsOn(settingsPatch)

    execute {
        registerSetting("assistant_car_screen")

        CarRuntimeAssistantFingerprint.method.apply {
            // The assistant's car app connection: kept, or null to open the assistant without it.
            val connection = instructions.indexOfFirst { it.calls(CAR_RUNTIME, "connection") }
            if (connection < 0) throw PatchException("connection() not found in CarRuntime.assistant")
            val register = resultRegister(instructions.toList(), connection, "CarRuntime.assistant connection")

            // Parked only, unless "Also while driving" is on (the one drivingRestricted read here).
            val driving = instructions.indexOfFirst { it.calls(UI_STATE, "getDrivingRestricted") }
            if (driving < 0) throw PatchException("getDrivingRestricted() not found in CarRuntime.assistant")
            if (driving < connection) throw PatchException("CarRuntime.assistant: drivingRestricted read before the connection")

            // The later insertion first, so the earlier index stays right.
            addInstructions(driving + 2, drivingFilter(resultRegister(instructions.toList(), driving, "CarRuntime.assistant drivingRestricted")))
            addInstructions(
                connection + 2,
                """
                    ${invokeStatic(register, "$ASSISTANT_CLASS->carApp(Ljava/lang/Object;)Ljava/lang/Object;")}
                    move-result-object v$register
                    check-cast v$register, $CONNECTION_CLASS
                """,
            )
        }

        CarRuntimeApplyScreenSettingsFingerprint.method.apply {
            // `if (ui.drivingRestricted) ui.assistantStandalonePkg = null`: the read right before the setter.
            val setters = instructions.withIndex().filter { it.value.calls(UI_STATE, "setAssistantStandalonePkg") }
            if (setters.size != 1) throw PatchException("applyScreenSettings: ${setters.size} setAssistantStandalonePkg calls, 1 expected")
            val driving = instructions.withIndex().lastOrNull { (i, instruction) ->
                i < setters[0].index && instruction.calls(UI_STATE, "getDrivingRestricted")
            }?.index ?: throw PatchException("applyScreenSettings: no drivingRestricted read before the assistant is closed")
            addInstructions(driving + 2, drivingFilter(resultRegister(instructions.toList(), driving, "applyScreenSettings drivingRestricted")))
        }

        CarAppConnectionFailFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $ASSISTANT_CLASS->carAppFailed(Ljava/lang/Object;)V",
        )
    }
}
