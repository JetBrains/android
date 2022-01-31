/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.material.icons.download

import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val HOST = "my.host.com"
private const val PATTERN = "/s/i/{family}/{icon}/v{version}/{asset}"
private const val OLD_METADATA_CONTENT =
  ")]}'\n" +
  "{\n" +
  "  \"host\": \"$HOST\",\n" +
  "  \"asset_url_pattern\": \"$PATTERN\",\n" +
  "  \"families\": [\n" +
  "    \"Style 1\"\n" +
  "  ],\n" +
  "  \"icons\": [\n" +
  "    {\n" +
  "      \"name\": \"my_icon_1\",\n" +
  "      \"version\": 1,\n" +
  "      \"unsupported_families\": [],\n" +
  "      \"categories\": [],\n" +
  "      \"tags\": []\n" +
  "    },\n" +
  "    {\n" +
  "      \"name\": \"my_icon_2\",\n" +
  "      \"version\": 1,\n" +
  "      \"unsupported_families\": [],\n" +
  "      \"categories\": [],\n" +
  "      \"tags\": []\n" +
  "    }\n" +
  "  ]\n" +
  "}"
private const val NEW_METADATA_CONTENT =
  ")]}'\n" +
  "{\n" +
  "  \"host\": \"$HOST\",\n" +
  "  \"asset_url_pattern\": \"$PATTERN\",\n" +
  "  \"families\": [\n" +
  "    \"Style 1\"\n" +
  "  ],\n" +
  "  \"icons\": [\n" +
  "    {\n" +
  "      \"name\": \"my_icon_1\",\n" +
  "      \"version\": 2,\n" +
  "      \"unsupported_families\": [],\n" +
  "      \"categories\": [],\n" +
  "      \"tags\": []\n" +
  "    }\n" +
  "  ]\n" +
  "}"
private const val OLD_VD = "old" // For this test, it doesn't matter if it's a valid Vector Drawable file
private const val NEW_VD = "new"

private const val ICON_DOWNLOAD_URL = "https://my.host.com/s/i/style1/my_icon_1/v2/24px.xml"

class MaterialIconsUpdaterTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var testDirectory: Path
  private lateinit var downloadDir: Path

  @Before
  fun setup() {
    testDirectory = createTempDirectory(javaClass.simpleName)
    downloadDir = testDirectory.resolve("downloads")
    // Setup 'Downloads' directory with the existing XML files of the `old` metadata
    downloadDir.resolve("style1/my_icon_1").apply { createDirectories() }.resolve("my_icon_1.xml").writeText(OLD_VD)
    downloadDir.resolve("style1/my_icon_2").apply { createDirectories() }.resolve("my_icon_2.xml").writeText(OLD_VD)

    // Setup mocked DownloadFileService, this will write a 'downloaded' file to the 'Downloads' directory when called properly
    val mockDownloadableFileService = projectRule.mockService(DownloadableFileService::class.java)
    val fileDescription = DownloadableFileDescriptionImpl(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon_1/my_icon_1"), "tmp")
    val mockDownloader = Mockito.mock(FileDownloader::class.java)
    `when`(mockDownloader.download(downloadDir.toFile())).thenAnswer {
      // Write file with the new file contents to the 'downloads' directory
      val downloadedFile = downloadDir.resolve(fileDescription.defaultFileName).apply {
        parent.createDirectories()
        writeText(NEW_VD)
      }.toFile()
      return@thenAnswer listOf(Pair(downloadedFile, fileDescription))
    }
    `when`(mockDownloadableFileService.createFileDescription(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon_1/style1_my_icon_1_24.tmp"))).thenReturn(fileDescription)
    `when`(mockDownloadableFileService.createDownloader(Mockito.any(), Mockito.eq("Material Icons"))).thenReturn(mockDownloader)
  }

  @Test
  fun updateIcons() {
    val existingIcon1 = downloadDir.resolve("style1/my_icon_1/my_icon_1.xml")
    val existingIcon2 = downloadDir.resolve("style1/my_icon_2/my_icon_2.xml")
    assertEquals(OLD_VD, existingIcon1.readText())
    assertEquals(OLD_VD, existingIcon2.readText())

    val newMetadata = MaterialIconsMetadata.parse(StringReader(NEW_METADATA_CONTENT))
    updateIconsAtDir(
      MaterialIconsMetadata.parse(StringReader(OLD_METADATA_CONTENT)),
      newMetadata,
      downloadDir
    )

    // Existing/old vector drawable files should not exist.
    assertFalse(existingIcon1.exists())
    assertFalse(existingIcon2.exists())

    // Only directory of my_icon_1 should exist
    assertThat(existingIcon1.parent).exists()
    assertFalse(existingIcon2.parent.exists())

    assertEquals(NEW_VD, existingIcon1.parent.resolve("style1_my_icon_1_24.xml").readText())

    // There should be a metadata file that reflects the new information
    val savedMetadata = downloadDir.resolve("icons_metadata.txt")
    assertTrue(savedMetadata.exists())
    assertEquals(MaterialIconsMetadata.parse(newMetadata), savedMetadata.readText())
  }
}