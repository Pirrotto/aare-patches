package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.MediaRecorder;

import org.junit.Test;

public class RecordingPauseTest {
    @Test
    public void voiceMessagesDictationAndRecordersCount() {
        assertTrue(RecordingPause.counts(MediaRecorder.AudioSource.MIC, false));
        assertTrue(RecordingPause.counts(MediaRecorder.AudioSource.VOICE_RECOGNITION, false));
        assertTrue(RecordingPause.counts(MediaRecorder.AudioSource.DEFAULT, false));
        assertTrue(RecordingPause.counts(MediaRecorder.AudioSource.CAMCORDER, false));
        assertTrue(RecordingPause.counts(MediaRecorder.AudioSource.UNPROCESSED, false));
    }

    @Test
    public void callsAndSilencedAppsDoNotPauseTheRadio() {
        // A call (WhatsApp's too) is the car's business; an app Android silences records nothing.
        assertFalse(RecordingPause.counts(MediaRecorder.AudioSource.VOICE_COMMUNICATION, false));
        assertFalse(RecordingPause.counts(MediaRecorder.AudioSource.VOICE_CALL, false));
        assertFalse(RecordingPause.counts(MediaRecorder.AudioSource.MIC, true));
    }
}
