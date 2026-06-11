/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fonts

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.download.DownloadableFileDescription
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FontFileDownloaderTest {

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun testMoveToDir_Normal() {
    val targetDir = tempFolder.newFolder("target")
    val tempFile = tempFolder.newFile("temp_font.ttf")
    FileUtil.writeToFile(tempFile, "font data")

    val description = mock(DownloadableFileDescription::class.java)
    // generateFileName takes a Condition/Predicate. In Java it might be com.intellij.openapi.util.Condition.
    // We use any() to match it.
    `when`(description.generateFileName(any())).thenReturn("safe_font.ttf")

    val downloadedFiles = listOf(Pair.create(tempFile, description))

    val result = FontFileDownloader.moveToDir(downloadedFiles, targetDir)

    assertEquals(1, result.size)
    val expectedFile = File(targetDir, "safe_font.ttf").canonicalFile
    assertEquals(expectedFile, result[0].first)
    assertTrue(expectedFile.exists())
    assertEquals("font data", FileUtil.loadFile(expectedFile))
    assertFalse(tempFile.exists()) // original temp file should be moved/deleted
  }

  @Test
  fun testMoveToDir_Traversal() {
    val targetDir = tempFolder.newFolder("target")
    val tempFile = tempFolder.newFile("temp_font.ttf")
    FileUtil.writeToFile(tempFile, "font data")

    val description = mock(DownloadableFileDescription::class.java)
    // Malicious filename attempting to escape targetDir
    `when`(description.generateFileName(any())).thenReturn("../traversed_font.ttf")

    val downloadedFiles = listOf(Pair.create(tempFile, description))

    val result = FontFileDownloader.moveToDir(downloadedFiles, targetDir)

    assertTrue(result.isEmpty()) // Should refuse to move and return empty

    val traversedFile = File(targetDir.parentFile, "traversed_font.ttf")
    assertFalse(traversedFile.exists()) // Should NOT write to traversed path
    assertFalse(tempFile.exists()) // Temp file should have been deleted for cleanup
  }

  @Test
  fun testMoveToDir_EmptyOrDotFilename() {
    val targetDir = tempFolder.newFolder("target")
    val tempFile = tempFolder.newFile("temp_font.ttf")
    FileUtil.writeToFile(tempFile, "font data")

    val description = mock(DownloadableFileDescription::class.java)
    // Filename resolves to "."
    `when`(description.generateFileName(any())).thenReturn(".")

    val downloadedFiles = listOf(Pair.create(tempFile, description))

    val result = FontFileDownloader.moveToDir(downloadedFiles, targetDir)

    assertTrue(result.isEmpty()) // Should refuse to move and return empty
    assertFalse(tempFile.exists()) // Temp file should have been deleted for cleanup
  }

  @Test
  fun testMoveToDir_EmptyFilename() {
    val targetDir = tempFolder.newFolder("target")
    val tempFile = tempFolder.newFile("temp_font.ttf")
    FileUtil.writeToFile(tempFile, "font data")

    val description = mock(DownloadableFileDescription::class.java)
    // Filename resolves to empty string
    `when`(description.generateFileName(any())).thenReturn("")

    val downloadedFiles = listOf(Pair.create(tempFile, description))

    val result = FontFileDownloader.moveToDir(downloadedFiles, targetDir)

    assertTrue(result.isEmpty()) // Should refuse to move and return empty
    assertFalse(tempFile.exists()) // Temp file should have been deleted for cleanup
  }
}
