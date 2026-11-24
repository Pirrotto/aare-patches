package app.template.patches.aare

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.shared.Constants.COMPATIBILITY_AARE
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/** CarSession's guidance focus, CarAudioOut.Focus.request(playing): asks the car for focus for the navigation channel. */
internal object GuidanceFocusRequestFingerprint : Fingerprint(
    definingClass = "Lnet/prototux/aare/car/CarSession\$guidanceFocus\$1;",
    name = "request",
    returnType = "V",
    parameters = listOf("Z"),
)

private const val RADIO_FOCUS = "Lapp/template/extension/aare/CarRadioFocus;"

@Suppress("unused")
val carRadioPausePatch = patch(
    name = "Car radio pause",
    description = "While AARE reads a message aloud and during a voice reply, the car's own sound (radio) pauses instead " +
        "of only getting lower, and stays paused until AARE is done, as with Android Auto's assistant. Navigation " +
        "prompts are unchanged. Off by default: turn it on in the patches' Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AARE)

    extendWith("extensions/extension.mpe")

    // AARE's own speech (reading, voice reply) is played by the voice messages patch.
    dependsOn(settingsPatch, voiceMessagesPatch)

    execute {
        registerSetting("radio_pause")

        GuidanceFocusRequestFingerprint.method.apply {
            // The may-duck type it asks for while a phrase plays: GAIN_TRANSIENT while AARE speaks.
            val index = instructions.indexOfFirst {
                ((it as? ReferenceInstruction)?.reference as? FieldReference)?.name == "AUDIO_FOCUS_GAIN_TRANSIENT_MAY_DUCK"
            }
            if (index < 0) throw PatchException("AUDIO_FOCUS_GAIN_TRANSIENT_MAY_DUCK not found in the guidance focus request")
            val register = (instructions[index] as OneRegisterInstruction).registerA
            val type = ((instructions[index] as ReferenceInstruction).reference as FieldReference).type
            addInstructions(
                index + 1,
                """
                    invoke-static { v$register }, $RADIO_FOCUS->guidanceType(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                    check-cast v$register, $type
                """,
            )

            // The release after a phrase: kept back while AARE is still speaking.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $RADIO_FOCUS->holdRelease(Ljava/lang/Object;Z)Z
                    move-result v0
                    if-eqz v0, :aare_radio
                    return-void
                """,
                ExternalLabel("aare_radio", getInstruction(0)),
            )
        }
    }
}
