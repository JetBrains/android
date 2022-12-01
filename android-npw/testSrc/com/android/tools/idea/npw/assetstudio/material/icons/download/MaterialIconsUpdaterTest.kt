/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.material.icons.download

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.utils.SdkUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import com.intellij.util.io.readText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals

private const val HOST = "my.host.com"
private const val PATTERN = "/s/i/{family}/{icon}/v{version}/{asset}"
private const val NOT_EXECUTABLE_PREFIX = ")]}'\n"
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
  private lateinit var existingMetadataFile: File

  @Before
  fun setup() {
    testDirectory = createTempDirectory(javaClass.simpleName)
    downloadDir = testDirectory.resolve("downloads")
    existingMetadataFile = downloadDir.resolve(METADATA_FILE_NAME).createFile().apply { writeText(OLD_METADATA_CONTENT) }.toFile()
    // Setup 'Downloads' directory with the existing XML files of the `old` metadata
    downloadDir.resolve("style1/my_icon_1").apply { createDirectories() }.resolve("my_icon_1.xml").writeText(OLD_VD)
    downloadDir.resolve("style1/my_icon_2").apply { createDirectories() }.resolve("my_icon_2.xml").writeText(OLD_VD)

    // Setup mocked DownloadFileService, this will write a 'downloaded' file to the 'Downloads' directory when called properly
    val mockDownloadableFileService = projectRule.mockService(DownloadableFileService::class.java)
    val fileDescription = DownloadableFileDescriptionImpl(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon_1/my_icon_1"), "tmp")
    val mockDownloader = Mockito.mock(FileDownloader::class.java)
    val downloadDirAsFile = downloadDir.toFile()
    whenever(mockDownloader.download(downloadDirAsFile)).thenAnswer {
      // Write file with the new file contents to the 'downloads' directory
      val downloadedFile = downloadDir.resolve(fileDescription.defaultFileName).apply {
        parent.createDirectories()
        writeText(NEW_VD)
      }.toFile()
      return@thenAnswer listOf(Pair(downloadedFile, fileDescription))
    }
    whenever(mockDownloadableFileService.createFileDescription(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon_1/style1_my_icon_1_24.tmp"))).thenReturn(fileDescription)
    whenever(mockDownloadableFileService.createDownloader(Mockito.any(), Mockito.eq("Material Icons"))).thenReturn(mockDownloader)
  }

  @Test
  fun updateIcons() {
    val existingIcon1 = downloadDir.resolve("style1/my_icon_1/my_icon_1.xml")
    val existingIcon2 = downloadDir.resolve("style1/my_icon_2/my_icon_2.xml")
    assertEquals(OLD_VD, existingIcon1.readText())
    assertEquals(OLD_VD, existingIcon2.readText())

    val loadExistingMetadata: () -> MaterialIconsMetadata = {
      MaterialIconsMetadata.parse(SdkUtils.fileToUrl(existingMetadataFile), thisLogger())
    }

    val testDownloadedMetadataFile = testDirectory.resolve("downloaded_metadata.txt").apply { writeText(NEW_METADATA_CONTENT) }.toFile()
    val loadTestDownloadedMetadata: () -> MaterialIconsMetadata = {
      MaterialIconsMetadata.parse(SdkUtils.fileToUrl(testDownloadedMetadataFile), thisLogger())
    }

    updateIconsAtDir(
      existingMetadata = loadExistingMetadata(),
      newMetadata = loadTestDownloadedMetadata(),
      targetDir = downloadDir
    )

    // Downloaded version of Icon 1 forces a specific file name and deletes the existing XML file
    assertThat(existingIcon1).doesNotExist()
    // Icon2 may still be in use, so it shouldn't be deleted yet, we just update the metadata file
    assertThat(existingIcon2).exists()

    // Icon directories should still exist
    assertThat(existingIcon1.parent).exists()
    assertThat(existingIcon2.parent).exists()

    assertEquals(NEW_VD, existingIcon1.parent.resolve("style1_my_icon_1_24.xml").readText())

    // The existing metadata file should reflect the new information, however it will be formatted differently, removing all whitespace
    // while keeping the not executable prefix
    val expectedMetadataContent = testDownloadedMetadataFile.readText().toExpectedJsonFormat()
    assertEquals(expectedMetadataContent, existingMetadataFile.readText())

    // Calling updateIconsAtDir again will remove any unused Icons from the current metadata
    updateIconsAtDir(
      existingMetadata = loadExistingMetadata(),
      newMetadata = loadTestDownloadedMetadata(),
      targetDir = downloadDir
    )

    // Icon 2 should be deleted now, since it's not in use by the current metadata
    assertThat(existingIcon1.parent).exists()
    assertThat(existingIcon2.parent).doesNotExist()
  }

  private fun String.toExpectedJsonFormat(): String {
    val expectedPrefixIndex = NOT_EXECUTABLE_PREFIX.length - 1
    var isInStringToken = false
    val builder = StringBuilder()

    this.forEachIndexed { index, c ->
      if (index <= expectedPrefixIndex) {
        builder.append(c)
        return@forEachIndexed
      }

      if ((c == ' ' || c == '\n') && !isInStringToken) {
        return@forEachIndexed
      }
      if (c == '\'' || c == '"') {
        isInStringToken = !isInStringToken
      }
      builder.append(c)
    }
    return builder.toString()
  }
}