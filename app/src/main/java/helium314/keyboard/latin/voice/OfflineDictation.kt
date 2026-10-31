// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.InputTypeUtils
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class OfflineDictation(private val ime: LatinIME) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor = Executors.newSingleThreadExecutor()
    private val worker = executor.asCoroutineDispatcher()
    private var recognizer: LocalRecognizer? = null
    private var job: Job? = null
    private var audio: LocalAudio? = null
    private var dialog: AlertDialog? = null
    private var warmup: Job? = null

    fun preload() {
        if (warmup?.isActive == true || !ModelStore(ime).installed()) return
        warmup = scope.launch {
            try {
                withContext(worker) {
                    if (recognizer == null) recognizer = LocalRecognizer(ModelStore(ime).directory)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Report failure when dictation is explicitly requested. */ }
            catch (_: LinkageError) { /* Report incompatible native runtime on explicit request. */ }
        }
    }

    fun toggle(token: IBinder) {
        if (job != null) { audio?.stop(); return }
        val editor = ime.currentInputEditorInfo ?: return
        if (InputTypeUtils.isPasswordInputType(editor.inputType) ||
            InputTypeUtils.isVisiblePasswordInputType(editor.inputType)) {
            message("Dictée désactivée dans les champs de mot de passe.")
            return
        }
        if (!ModelStore(ime).installed() || ContextCompat.checkSelfPermission(ime,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ime.startActivity(Intent(ime, VoiceSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val connection = ime.currentInputConnection ?: return
        val capture = LocalAudio()
        audio = capture
        val popup = AlertDialog.Builder(ime)
            .setTitle("Dictée hors ligne")
            .setMessage("Chargement du modèle… Attendre avant de parler.")
            .setPositiveButton("Terminer", null)
            .setNegativeButton("Annuler") { _, _ -> cancel() }
            .setOnCancelListener { cancel() }
            .create()
        popup.window?.apply {
            attributes = attributes.apply {
                this.token = token
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog = popup
        popup.setCanceledOnTouchOutside(false)
        popup.show()
        popup.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            setOnClickListener {
                isEnabled = false
                popup.setMessage("Transcription locale…")
                capture.stop()
            }
        }
        job = scope.launch {
            try {
                val text = withContext(worker) {
                    if (recognizer == null) recognizer = LocalRecognizer(ModelStore(ime).directory)
                    ensureActive()
                    val samples = capture.record {
                        scope.launch {
                            if (dialog === popup) {
                                popup.setMessage("Parlez maintenant. Ponctuation automatique.\nMaximum : 60 secondes.")
                                popup.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        popup.setMessage("Transcription locale…")
                        popup.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    }
                    ensureActive()
                    if (samples.size < 1600) "" else recognizer!!.transcribe(samples)
                }
                ensureActive()
                // Never send a delayed result into a different app or editor.
                if (ime.currentInputConnection === connection && ime.currentInputEditorInfo === editor) {
                    if (text.isNotBlank()) ime.onTextInput(text)
                    else message("Aucune parole reconnue.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message("Échec de la dictée locale. Vérifier le micro, le modèle et la mémoire disponible.")
            } catch (_: LinkageError) {
                message("Moteur vocal incompatible avec cet appareil.")
            } finally {
                if (dialog === popup) {
                    popup.dismiss()
                    dialog = null
                    audio = null
                    job = null
                }
            }
        }
    }

    fun cancel() {
        audio?.stop()
        job?.cancel()
        job = null
        audio = null
        dialog?.dismiss()
        dialog = null
    }
    fun close() {
        cancel()
        scope.cancel()
        // Queue release after any uninterruptible JNI decode, never block the IME thread.
        executor.execute { recognizer?.close(); recognizer = null }
        executor.shutdown()
    }
    private fun message(text: String) = Toast.makeText(ime, text, Toast.LENGTH_LONG).show()
}
