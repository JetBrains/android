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

import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val HOST = "my.host.com"
private const val PATTERN = "/s/i/{family}/{icon}/v{version}/{asset}"
private const val METADATA_FORMAT =
  ")]}'\n" +
  "{\n" +
  "  \"host\": \"$HOST\",\n" +
  "  \"asset_url_pattern\": \"$PATTERN\",\n" +
  "  \"families\": [\n" +
  "    \"Style 1\"\n" +
  "  ],\n" +
  "  \"icons\": [\n" +
  "    {\n" +
  "      \"name\": \"my_icon\",\n" +
  "      \"version\": %1s,\n" +
  "      \"unsupported_families\": [],\n" +
  "      \"categories\": [],\n" +
  "      \"tags\": []\n" +
  "    }\n" +
  "  ]\n" +
  "}"
private const val OLD_VD = "old" // For this test, it doesn't matter if it's a valid Vector Drawable file
private const val NEW_VD = "new"

private val OLD_METADATA = MaterialIconsMetadata.parse(StringReader(METADATA_FORMAT.format("1")))
private val NEW_METADATA = MaterialIconsMetadata.parse(StringReader(METADATA_FORMAT.format("2")))

private const val ICON_DOWNLOAD_URL = "https://my.host.com/s/i/style1/my_icon/v2/24px.xml"

class MaterialIconsDownloaderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var testDirectory: File
  private lateinit var materialIconsDownloader: MaterialIconsDownloader

  @Before
  fun setup() {
    testDirectory = FileUtil.createTempDirectory(javaClass.simpleName, null)
    val downloadDir = testDirectory.resolve("downloads")
    // Setup 'Downloads' directory with an existing XML file, the 'old' Vector Drawable file
    downloadDir.resolve("style1").resolve("my_icon").apply { mkdirs() }.resolve("my_icon.xml").writeText(OLD_VD)

    // Setup mocked DownloadFileService, this will write a 'downloaded' file to the 'Downloads' directory when called properly
    val mockDownloadableFileService = projectRule.mockService(DownloadableFileService::class.java)
    val fileDescription = DownloadableFileDescriptionImpl(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon/my_icon"), "tmp")
    val mockDownloader = Mockito.mock(FileDownloader::class.java)
    Mockito.`when`(mockDownloader.download(downloadDir)).thenAnswer {
      // Write file with the new file contents to the 'downloads' directory
      val downloadedFile = downloadDir.resolve(FileUtil.toSystemDependentName(fileDescription.defaultFileName)).apply {
        parentFile.mkdirs()
        writeText(NEW_VD)
      }
      return@thenAnswer listOf(Pair(downloadedFile, fileDescription))
    }
    Mockito.`when`(mockDownloadableFileService.createFileDescription(
      ICON_DOWNLOAD_URL, FileUtil.toSystemDependentName("style1/my_icon/style1_my_icon_24.tmp"))).thenReturn(fileDescription)
    Mockito.`when`(mockDownloadableFileService.createDownloader(Mockito.any(), Mockito.eq("Material Icons"))).thenReturn(mockDownloader)
    materialIconsDownloader = MaterialIconsDownloader(OLD_METADATA, NEW_METADATA)
  }

  @Test
  fun downloadIcons() {
    val downloadDir = testDirectory.resolve("downloads")
    assertEquals(1, downloadDir.list()!!.size)
    val existingIcon = downloadDir.resolve("style1").resolve("my_icon").resolve("my_icon.xml")
    assertEquals(OLD_VD, existingIcon.readText())

    materialIconsDownloader.downloadTo(downloadDir)

    // Existing/old vector drawable file should not exist.
    assertFalse(existingIcon.exists())
    assertEquals(2, downloadDir.list()!!.size)
    assertEquals(NEW_VD, existingIcon.parentFile.resolve("style1_my_icon_24.xml").readText())
    val savedMetadata = downloadDir.resolve("icons_metadata.txt")
    // There should be a metadata file that reflects the new information
    assertEquals(MaterialIconsMetadata.parse(NEW_METADATA), savedMetadata.readText())
  }

}