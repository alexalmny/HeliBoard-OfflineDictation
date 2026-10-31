// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF) // Filesystem tests do not need native TLS (unavailable on Linux ARM64).
class ModelStoreTest {
    private lateinit var store: ModelStore
    @Before fun setup() {
        store = ModelStore(RuntimeEnvironment.getApplication())
        store.directory.deleteRecursively()
        store.directory.mkdirs()
    }
    @After fun cleanup() { store.directory.deleteRecursively() }
    @Test fun partialDownloadIsNeverReady() {
        File(store.directory, "encoder.int8.onnx.part").writeText("partial")
        assertFalse(store.installed())
        File(store.directory, "verified").writeText("marker alone is insufficient")
        assertFalse(store.installed())
    }
    @Test fun missingOrTruncatedComponentInvalidatesInstallation() {
        for (spec in ModelFiles.specs) {
            RandomAccessFile(File(store.directory, spec.name), "rw").use { it.setLength(spec.size) }
        }
        assertFalse(store.installed())
        File(store.directory, "verified").writeText("verified")
        assertTrue(store.installed()) // Full checksum verification happens before native loading.
        File(store.directory, "tokens.txt").writeText("truncated")
        assertFalse(store.installed())
    }
}
