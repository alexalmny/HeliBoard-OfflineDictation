// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/** Access only on the dictation worker; JNI decode cannot be interrupted safely. */
class LocalRecognizer(directory: File) : AutoCloseable {
    private val recognizer: OfflineRecognizer
    init {
        check(ModelFiles.specs.all { ModelFiles.valid(File(directory, it.name), it) }) {
            "Modèle corrompu. Relancer son téléchargement dans les réglages."
        }
        recognizer = OfflineRecognizer(null, OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(directory, "encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "decoder.int8.onnx").absolutePath,
                    joiner = File(directory, "joiner.int8.onnx").absolutePath,
                ),
                tokens = File(directory, "tokens.txt").absolutePath,
                modelType = "nemo_transducer", numThreads = 2, debug = false, provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        ))
    }
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally { stream.release() }
    }
    override fun close() = recognizer.release()
}
