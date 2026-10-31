// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

class LocalAudio {
    @Volatile private var stop = false
    fun stop() { stop = true }

    @SuppressLint("MissingPermission") // Checked by OfflineDictation before entering the worker.
    suspend fun record(onStarted: () -> Unit): FloatArray {
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Format audio non pris en charge." }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 6400))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone indisponible." }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone indisponible." }
            onStarted()
            // shortcut: 60 seconds per turn bounds RAM and decode time; add segmentation for long dictations.
            val samples = FloatArray(16000 * 60)
            val buffer = ShortArray(1600)
            var size = 0
            while (!stop && size < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, samples.size - size), AudioRecord.READ_NON_BLOCKING)
                check(n >= 0) { "Lecture du microphone interrompue ($n)." }
                for (i in 0 until n) samples[size++] = buffer[i] / 32768f
                if (n == 0) delay(10)
            }
            return samples.copyOf(size)
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally { recorder.release() }
        }
    }
}
