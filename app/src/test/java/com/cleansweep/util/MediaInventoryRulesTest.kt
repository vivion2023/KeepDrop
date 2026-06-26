/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 */

package com.cleansweep.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class MediaInventoryRulesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isInAlbumFolders_matchesCanonicalParent() {
        val album = tempFolder.newFolder("DCIM", "Camera")
        val photo = File(album, "shot.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(isInAlbumFolders(photo, setOf(album.canonicalPath)))
    }

    @Test
    fun isInAlbumFolders_rejectsOtherFolder() {
        val album = tempFolder.newFolder("DCIM", "Camera")
        val other = tempFolder.newFolder("Pictures")
        val photo = File(other, "shot.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(isInAlbumFolders(photo, setOf(album.canonicalPath)))
    }

    @Test
    fun isDisplayableMediaFile_rejectsEmptyFile() {
        val file = tempFolder.newFile("blank.jpg")
        assertFalse(isDisplayableMediaFile(file, isVideo = false))
    }

    @Test
    fun isDisplayableMediaFile_rejectsHiddenFile() {
        val file = tempFolder.newFile(".hidden.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(isDisplayableMediaFile(file, isVideo = false))
    }

    @Test
    fun isExcludedMediaFileName_rejectsDotTrashedMarker() {
        assertTrue(isExcludedMediaFileName(".trashed-1784469130-1781877088139.jpg"))
    }

    @Test
    fun isExcludedMediaFileName_rejectsTrashedWithoutLeadingDot() {
        assertTrue(isExcludedMediaFileName("trashed-178445903-idlefish-msg-1781.jpg"))
    }

    @Test
    fun isExcludedMediaFileName_allowsNormalCapture() {
        assertFalse(isExcludedMediaFileName("AQ_IMG_1767922517709.jpg"))
    }
}