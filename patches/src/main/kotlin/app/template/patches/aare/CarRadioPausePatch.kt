package app.template.patches.aare

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
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
private const val RECORDING_PAUSE = "Lapp/template/extension/aare/RecordingPause;"
private const val CAR_SESSION = "Lnet/prototux/aare/car/CarSession;"
private const val AAP_SESSION = "Lnet/prototux/aare/aap/AapSession;"

/** CarSession.onRunning(session): the car is connected and running. */
internal object CarSessionOnRunningFingerprint : Fingerprint(
    definingClass = CAR_SESSION,
    name = "onRunning",
    returnType = "V",
    parameters = listOf(AAP_SESSION),
)

/** CarSession.onClosed(session, reason, error): the car connection ended. */
internal object CarSessionOnClosedFingerprint : Fingerprint(
    definingClass = CAR_SESSION,
    name = "onClosed",
    returnType = "V",
    parameters = listOf(AAP_SESSION, "Ljava/lang/String;", "Ljava/lang/Throwable;"),
)

@Suppress("unused")
val carRadioPausePatch = patch(
    name = "Car radio pause",
    description = "The car's radio pauses while AARE reads a message or you reply by voice, instead of only getting quieter; optionally also while a phone app records. Navigation prompts unchanged. Off by default.",
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

        // "While an app records": recordings are watched only while a car is connected.
        // The range form reaches p0 whatever the method's register count.
        CarSessionOnRunningFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $RECORDING_PAUSE->onSession(Ljava/lang/Object;)V",
        )
        CarSessionOnClosedFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $RECORDING_PAUSE->onClosed(Ljava/lang/Object;)V",
        )
    }
}
