// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*

class VoiceSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private lateinit var download: Button
    private lateinit var microphone: Button
    private val store by lazy { ModelStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Dictée hors ligne"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun text(value: String) = TextView(this).also {
            it.text = value
            it.textSize = 17f
            it.setPadding(0, 16, 0, 16)
            content.addView(it)
        }
        text("HeliBoard · Dictée locale\n\nParakeet TDT v3 INT8 — français et 24 autres langues, détection automatique, ponctuation et majuscules.\n\nUne connexion est nécessaire uniquement pour installer le modèle (~670 Mo). Ensuite, le mode avion fonctionne : aucun audio ni texte dicté n’est envoyé à un serveur ni conservé dans un journal.")
        text("1. Autoriser le microphone.\n2. Télécharger le modèle (prévoir 800 Mo libres). Garder cet écran ouvert.\n3. Activer ce clavier dans Android et choisir une disposition française dans les réglages HeliBoard.\n4. Dans un champ de texte, toucher le micro de la barre d’outils, attendre « Parlez maintenant », puis Terminer.\n\nLa transcription arrive après l’arrêt, pas en streaming. La vitesse dépend du téléphone ; un appareil ARM64 récent et 6 Go de RAM ou plus sont conseillés. Maximum : 60 secondes par dictée.")
        microphone = Button(this).apply {
            text = "Autoriser le microphone"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
            content.addView(this)
        }
        status = text("").apply {
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        download = Button(this).apply {
            text = "Télécharger / réparer le modèle"
            setOnClickListener { install() }
            content.addView(this)
        }
        Button(this).apply {
            text = "Fermer"
            setOnClickListener { finish() }
            content.addView(this)
        }
        text("Modèle : NVIDIA Parakeet TDT 0.6B v3, CC BY 4.0 ; export ONNX INT8 : csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 (Hugging Face). Moteur sherpa-onnx, Apache 2.0 ; HeliBoard et cette intégration, GPL 3.\n\nLe téléchargement contacte Hugging Face et ses hébergeurs (adresse IP et requêtes de fichiers visibles pour eux). Pas de clé API, pas de compte, pas de cloud pour la dictée.")
        setContentView(ScrollView(this).apply { addView(content) })
        refresh()
    }
    override fun onResume() { super.onResume(); if (::microphone.isInitialized) refresh() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
    private fun refresh() {
        microphone.isEnabled = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        status.text = if (store.installed()) "Modèle installé et vérifié. Prêt pour une dictée hors ligne." else "Modèle non installé."
    }
    private fun install() {
        download.isEnabled = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var last = -1
                    store.download { percent ->
                        if (percent != last) {
                            last = percent
                            scope.launch { status.text = "Téléchargement et vérification : $percent %" }
                        }
                    }
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "Installation interrompue : ${e.message}\nRéessayer ; les fichiers complets et vérifiés sont réutilisés."
            } finally { download.isEnabled = true }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
