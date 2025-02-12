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
package com.android.tools.idea.material.icons.download

import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.android.tools.idea.testing.disposable
import com.android.utils.SdkUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals

private const val HOST = "my.host.com"
private const val PATTERN = "/s/i/{family}/{icon}/v{version}/{asset}"
private const val NOT_EXECUTABLE_PREFIX = ")]}'\n"
private const val OLD_METADATA_CONTENT = """)]}'
{
  "host": "$HOST",
  "asset_url_pattern": "$PATTERN",
  "families": [
    "Style 1"
  ],
  "icons": [
    {
      "name": "my_icon_1",
      "version": 1,
      "unsupported_families": [],
      "categories": [],
      "tags": []
    },
    {
      "name": "my_icon_2",
      "version": 1,
      "unsupported_families": [],
      "categories": [],
      "tags": []
    }
  ]
}
"""
private const val NEW_METADATA_CONTENT = """)]}'
{
  "host": "$HOST",
  "asset_url_pattern": "$PATTERN",
  "families": [
    "Style 1"
  ],
  "icons": [
    {
      "name": "my_icon_1",
      "version": 2,
      "unsupported_families": [],
      "categories": [],
      "tags": [
        "plaît",
        "respond",
        "répondez"
      ]
    }
  ]
}
"""

private const val OLD_VD =
  "old" // For this test, it doesn't matter if it's a valid Vector Drawable file
private const val NEW_VD = "new"

data class FakeDownload(val url: String, val downloadPath: String, val destinationPath: String)

class MaterialIconsUpdaterTest {
  @get:Rule val projectRule = ProjectRule()

  private lateinit var testDirectory: Path
  private lateinit var downloadDir: Path
  private lateinit var existingMetadataFile: File
  private lateinit var iconsUrlProvider: MaterialIconsUrlProvider

  /**
   * Provides a mock [DownloadableFileService] that will respond to downloads from the given
   * [iconDownloadUrl].
   *
   * The files map contains the relative path of the file and the contents to download.
   */
  private fun mockDownloadService(downloads: List<FakeDownload>) {
    // Setup mocked DownloadFileService, this will write a 'downloaded' file to the 'Downloads'
    // directory when called properly
    val mockDownloadableFileService = Mockito.mock(DownloadableFileService::class.java)
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(
        DownloadableFileService::class.java,
        mockDownloadableFileService,
        projectRule.disposable,
      )
    val mockDownloader = Mockito.mock(FileDownloader::class.java)
    val downloadDirAsFile = downloadDir.toFile()

    downloads.forEach {
      val descriptor =
        DownloadableFileDescriptionImpl(
          it.url,
          FileUtil.toSystemDependentName(it.downloadPath),
          "tmp",
        )

      whenever(
          mockDownloadableFileService.createFileDescription(
            it.url,
            FileUtil.toSystemDependentName(it.destinationPath),
          )
        )
        .thenReturn(descriptor)

      // Mock the download call
      whenever(mockDownloader.download(downloadDirAsFile)).thenAnswer {
        // Write file with the new file contents to the 'downloads' directory
        val downloadedFile =
          downloadDir
            .resolve(descriptor.defaultFileName)
            .apply {
              parent.createDirectories()
              writeText(NEW_VD)
            }
            .toFile()
        return@thenAnswer listOf(Pair(downloadedFile, descriptor))
      }
    }
    whenever(
        mockDownloadableFileService.createDownloader(Mockito.any(), Mockito.eq("Material Icons"))
      )
      .thenReturn(mockDownloader)
  }

  @Before
  fun setup() {
    testDirectory = createTempDirectory(javaClass.simpleName)
    downloadDir = testDirectory.resolve("downloads")
    existingMetadataFile =
      downloadDir
        .resolve(METADATA_FILE_NAME)
        .createParentDirectories()
        .createFile()
        .apply { writeText(OLD_METADATA_CONTENT) }
        .toFile()

    downloadDir
      .resolve("style1/my_unused_icon")
      .apply { createDirectories() }
      .resolve("style1_my_unused_icon_24.xml")
      .writeText(OLD_VD)

    // Setup 'Downloads' directory with the existing XML files of the `old` metadata
    downloadDir
      .resolve("style1/my_icon_1")
      .apply { createDirectories() }
      .resolve("style1_my_icon_1_24.xml")
      .writeText(OLD_VD)
    downloadDir
      .resolve("style1/my_icon_2")
      .apply { createDirectories() }
      .resolve("style1_my_icon_2_24.xml")
      .writeText(OLD_VD)

    iconsUrlProvider =
      object : MaterialIconsUrlProvider {
        override fun getStyleUrl(style: String): URL? =
          downloadDir.resolve(style.toDirFormat()).toUri().toURL()

        override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? =
          downloadDir
            .resolve(style.toDirFormat())
            .resolve(iconName)
            .resolve(iconFileName)
            .toUri()
            .toURL()
      }
  }

  @Test
  fun updateIcons() {
    mockDownloadService(
      listOf(
        FakeDownload(
          url = "https://my.host.com/s/i/style1/my_icon_1/v2/24px.xml",
          downloadPath = "style1/my_icon_1/my_icon_1",
          destinationPath = "style1/my_icon_1/style1_my_icon_1_24.tmp",
        )
      )
    )
    val unusedIcon = downloadDir.resolve("style1/my_unused_icon/style1_my_unused_icon_24.xml")
    val existingIcon1 = downloadDir.resolve("style1/my_icon_1/style1_my_icon_1_24.xml")
    val existingIcon2 = downloadDir.resolve("style1/my_icon_2/style1_my_icon_2_24.xml")
    // Verify that all exist
    assertEquals(OLD_VD, unusedIcon.readText())
    assertEquals(OLD_VD, existingIcon1.readText())
    assertEquals(OLD_VD, existingIcon2.readText())

    val loadExistingMetadata: () -> MaterialIconsMetadata = {
      MaterialIconsMetadata.parse(SdkUtils.fileToUrl(existingMetadataFile)).getOrThrow()
    }

    val testDownloadedMetadataFile =
      testDirectory
        .resolve("downloaded_metadata.txt")
        .apply { writeText(NEW_METADATA_CONTENT) }
        .toFile()
    val loadTestDownloadedMetadata: () -> MaterialIconsMetadata = {
      MaterialIconsMetadata.parse(SdkUtils.fileToUrl(testDownloadedMetadataFile)).getOrThrow()
    }

    assertTrue(
      updateIconsAtDir(
        existingMetadata = loadExistingMetadata(),
        newMetadata = loadTestDownloadedMetadata(),
        targetDir = downloadDir,
        iconsUrlProvider = iconsUrlProvider,
      )
    )

    // Unused icon should have been removed
    assertThat(unusedIcon).doesNotExist()
    assertThat(existingIcon1).exists()
    assertThat(existingIcon2).exists()

    // Icon directories should still exist
    assertThat(existingIcon1.parent).exists()
    assertThat(existingIcon2.parent).exists()

    assertEquals(NEW_VD, existingIcon1.parent.resolve("style1_my_icon_1_24.xml").readText())

    // The existing metadata file should reflect the new information, however it will be formatted
    // differently, removing all whitespace
    // while keeping the not executable prefix
    val expectedMetadataContent = testDownloadedMetadataFile.readText().toExpectedJsonFormat()
    assertEquals(expectedMetadataContent, existingMetadataFile.readText())

    // Calling updateIconsAtDir again will remove any unused Icons from the current metadata.
    // Because the metadata is already up to date, updateIconsAtDir will return false.
    assertFalse(
      updateIconsAtDir(
        existingMetadata = loadExistingMetadata(),
        newMetadata = loadTestDownloadedMetadata(),
        targetDir = downloadDir,
        iconsUrlProvider = iconsUrlProvider,
      )
    )

    // Icon 2 should be deleted now, since it's not in use by the current metadata
    assertThat(existingIcon1.parent).exists()
    assertThat(existingIcon2.parent).doesNotExist()
  }

  @Test
  fun reDownloadBrokenIcons() {
    mockDownloadService(
      listOf(
        FakeDownload(
          url = "https://my.host.com/s/i/style1/my_icon_1/v1/24px.xml",
          downloadPath = "style1/my_icon_1/my_icon_1",
          destinationPath = "style1/my_icon_1/style1_my_icon_1_24.tmp",
        )
      )
    )
    val existingIcon1 = downloadDir.resolve("style1/my_icon_1/style1_my_icon_1_24.xml")
    val existingIcon2 = downloadDir.resolve("style1/my_icon_2/style1_my_icon_2_24.xml")

    existingIcon1.delete()

    assertTrue(
      updateIconsAtDir(
        existingMetadata =
          MaterialIconsMetadata.parse(SdkUtils.fileToUrl(existingMetadataFile)).getOrThrow(),
        newMetadata =
          MaterialIconsMetadata.parse(SdkUtils.fileToUrl(existingMetadataFile)).getOrThrow(),
        targetDir = downloadDir,
        iconsUrlProvider = iconsUrlProvider,
      )
    )

    assertThat(existingIcon1).exists()
    assertThat(existingIcon2).exists()
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
