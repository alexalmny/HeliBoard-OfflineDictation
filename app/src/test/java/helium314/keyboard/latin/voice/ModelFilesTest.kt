// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ModelFilesTest {
    @Test fun acceptsOnlyExactSizeAndChecksum() {
        val file = Files.createTempFile("model-test", ".bin").toFile()
        try {
            file.writeText("abc")
            val spec = ModelFiles.Spec("test", 3,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            assertTrue(ModelFiles.valid(file, spec))
            file.writeText("abd")
            assertFalse(ModelFiles.valid(file, spec))
            file.writeText("ab")
            assertFalse(ModelFiles.valid(file, spec))
            file.writeText("abcd")
            assertFalse(ModelFiles.valid(file, spec))
            file.delete()
            assertFalse(ModelFiles.valid(file, spec))
        } finally { file.delete() }
    }
    @Test fun pinsAllModelComponents() {
        assertEquals(4, ModelFiles.specs.size)
        assertEquals(670478772L, ModelFiles.specs.sumOf { it.size })
        assertEquals(4, ModelFiles.specs.map { it.name }.toSet().size)
        assertTrue(ModelFiles.specs.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
        assertFalse(ModelFiles.BASE_URL.contains("/main"))
    }
}
