// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelStore(context: Context) {
    val directory = File(context.noBackupFilesDir, "parakeet-v3-int8")
    fun installed() = File(directory, "verified").isFile &&
        ModelFiles.specs.all { File(directory, it.name).length() == it.size }

    // Only this explicit installer uses the network. Dictation never calls it.
    suspend fun download(progress: (Int) -> Unit) = installMutex.withLock {
        check(directory.exists() || directory.mkdirs()) { "Impossible de créer le dossier du modèle." }
        check(directory.usableSpace > ModelFiles.specs.sumOf { it.size } + 100_000_000) {
            "Prévoir au moins 800 Mo de stockage libre."
        }
        File(directory, "verified").delete()
        val total = ModelFiles.specs.sumOf { it.size }
        var received = 0L
        for (spec in ModelFiles.specs) {
            currentCoroutineContext().ensureActive()
            val target = File(directory, spec.name)
            if (target.isFile && ModelFiles.valid(target, spec)) {
                received += spec.size
                progress((received * 100 / total).toInt())
                continue
            }
            val temp = File(directory, "${spec.name}.part")
            try {
                val connection = openHttps("${ModelFiles.BASE_URL}/${spec.name}")
                try {
                    check(connection.responseCode == 200) { "Téléchargement refusé : HTTP ${connection.responseCode}" }
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            var count = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                count += n
                                check(count <= spec.size) { "Fichier trop volumineux." }
                                output.write(buffer, 0, n)
                                progress(((received + count) * 100 / total).toInt())
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                check(ModelFiles.valid(temp, spec)) { "Vérification SHA-256 échouée : ${spec.name}" }
                check(!target.exists() || target.delete()) { "Impossible de remplacer ${spec.name}" }
                check(temp.renameTo(target)) { "Impossible d’installer ${spec.name}" }
                received += spec.size
            } finally {
                temp.delete()
            }
        }
        File(directory, "verified").writeText("2bda32ec70b097a55adaa07d9a7173915b43cc78")
    }

    companion object { private val installMutex = Mutex() }

    private fun openHttps(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(8) {
            check(url.protocol == "https") { "Redirection non HTTPS refusée." }
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            val code = try { connection.responseCode } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            if (code !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            check(location != null) { "Redirection invalide." }
            url = URL(url, location)
        }
        error("Trop de redirections.")
    }
}
