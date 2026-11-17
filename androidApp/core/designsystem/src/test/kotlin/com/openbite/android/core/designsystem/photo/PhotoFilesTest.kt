package com.openbite.android.core.designsystem.photo

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoFilesTest {
    private val root = Files.createTempDirectory("files").toFile()

    @Test
    fun onlyPathsInsidePhotosAreAllowed() {
        assertEquals(java.io.File(root, "photos/a.jpg").canonicalFile, PhotoFiles.photoFile(root, "photos/a.jpg"))
        assertNull(PhotoFiles.photoFile(root, "photosX/a.jpg"), "前缀相同的兄弟目录不算")
        assertNull(PhotoFiles.photoFile(root, "photos/../secrets.pb"))
        assertNull(PhotoFiles.photoFile(root, "/etc/passwd"))
        assertNull(PhotoFiles.photoFile(root, "photos"))
    }

    @Test
    fun seedPathsCannotClimbOut() {
        assertTrue(PhotoFiles.isSeed("seed/minato-1.jpg"))
        assertFalse(PhotoFiles.isSeed("seed/../../x"))
    }
}
