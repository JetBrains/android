/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

private const val OLD_FILE_CONTENT =
  "old" // For this test, it doesn't matter if it's a valid Vector Drawable file
private const val NEW_FILE_CONTENT = "new"

class MaterialSymbolsUpdaterTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private lateinit var testDirectory: Path
  private lateinit var downloadDir: Path

  private fun mockDownloadService(downloads: List<FakeDownload>) {
    val mockDownloadableFileService = Mockito.mock(DownloadableFileService::class.java)
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(
        DownloadableFileService::class.java,
        mockDownloadableFileService,
        projectRule.fixture.testRootDisposable,
      )

    val mockDownloader = Mockito.mock(FileDownloader::class.java)

    downloads.forEach {
      val descriptor =
        DownloadableFileDescriptionImpl(
          it.url,
          FileUtil.toSystemDependentName(it.destinationPath),
          "tmp",
        )

      whenever(mockDownloadableFileService.createFileDescription(it.url, it.downloadPath))
        .thenReturn(descriptor)

      whenever(mockDownloader.download(Mockito.any())).thenAnswer {
        val downloadedFile =
          downloadDir
            .resolve(descriptor.defaultFileName)
            .apply {
              parent.createDirectories()
              writeText(NEW_FILE_CONTENT)
            }
            .toFile()
        return@thenAnswer listOf(
          Pair<File, DownloadableFileDescription>(downloadedFile, descriptor)
        )
      }
    }

    whenever(
        mockDownloadableFileService.createDownloader(
          Mockito.any(),
          Mockito.argThat {
            listOf("MaterialSymbolsFont", "MaterialSymbolsMetadata", "PickedMaterialSymbol")
              .contains(it)
          },
        )
      )
      .thenReturn(mockDownloader)
  }

  @Before
  fun setup() {
    testDirectory = createTempDirectory(javaClass.simpleName)
    downloadDir = testDirectory.resolve("downloads")
  }

  @Test
  fun updateFontFile() {
    val testStyle = Symbols.OUTLINED

    val downloadUrl = "https://my.host.com/s/i/style1/my_icon_1/v2/24px.xml"
    val downloadPathDir = "variablefont/${testStyle.localName}"
    val downloadName = "${testStyle.localName}.ttf"

    downloadDir
      .resolve(downloadPathDir)
      .apply { createDirectories() }
      .resolve(downloadName)
      .writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = downloadUrl,
          downloadPath = testStyle.remoteFileName,
          destinationPath = "${downloadPathDir}/${downloadName}",
        )
      )
    )

    val fontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())

    MaterialSymbolsUpdater.downloadFontFiles(URL(downloadUrl), testStyle)

    val updatedFontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun downloadFontFile() {
    val testStyle = Symbols.OUTLINED

    val downloadUrl = "https://my.host.com/s/i/style1/my_icon_1/v2/24px.xml"
    val downloadPathDir = "variablefont/${testStyle.localName}"
    val downloadName = "${testStyle.localName}.ttf"

    downloadDir
      .resolve(downloadPathDir)
      .apply { createDirectories() }
      .resolve(downloadName)
      .writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = downloadUrl,
          downloadPath = testStyle.remoteFileName,
          destinationPath = "${downloadPathDir}/${downloadName}",
        )
      )
    )

    val fontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadFontFiles(URL(downloadUrl), testStyle)

    val updatedFontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun downloadMetadataFile() {
    val downloadUrl = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    val tempDownloadName = "icons_metadata_temp.txt"
    val finalDownloadName = "icons_metadata.txt"

    downloadDir.apply { createDirectories() }.resolve(tempDownloadName).writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = downloadUrl,
          downloadPath = tempDownloadName,
          destinationPath = tempDownloadName,
        )
      )
    )

    val fontFile = downloadDir.resolve(tempDownloadName)
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadMetadataFile()

    val updatedFontFile = downloadDir.resolve(finalDownloadName)
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun updateMetadataFile() {
    val downloadUrl = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    val tempDownloadName = "icons_metadata_temp.txt"
    val finalDownloadName = "icons_metadata.txt"

    downloadDir.apply { createDirectories() }.resolve(tempDownloadName).writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = downloadUrl,
          downloadPath = tempDownloadName,
          destinationPath = tempDownloadName,
        )
      )
    )

    val fontFile = downloadDir.resolve(tempDownloadName)
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())

    MaterialSymbolsUpdater.downloadMetadataFile()

    val updatedFontFile = downloadDir.resolve(finalDownloadName)
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun downloadVdIcons() {
    val symbolConfiguration = SymbolConfiguration.DEFAULT
    val symbolName = "10k"
    val downloadUrl = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    val downloadPathDir = "${symbolConfiguration.type.localName}/${symbolName}"
    val downloadName = symbolConfiguration.toFileName(symbolName)

    downloadDir
      .resolve(downloadPathDir)
      .apply { createDirectories() }
      .resolve(downloadName)
      .writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = symbolConfiguration.toUrlString(symbolName),
          downloadPath = symbolName,
          destinationPath = "${downloadPathDir}/${downloadName}",
        )
      )
    )

    val fontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadVdIcon(symbolConfiguration, symbolName)

    val updatedFontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun updateVdIcons() {
    val symbolConfiguration = SymbolConfiguration.DEFAULT
    val symbolName = "10k"
    val downloadUrl = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    val downloadPathDir = "${symbolConfiguration.type.localName}/${symbolName}"
    val downloadName = symbolConfiguration.toFileName(symbolName)

    downloadDir
      .resolve(downloadPathDir)
      .apply { createDirectories() }
      .resolve(downloadName)
      .writeText(OLD_FILE_CONTENT)

    mockDownloadService(
      listOf(
        FakeDownload(
          url = symbolConfiguration.toUrlString(symbolName),
          downloadPath = symbolName,
          destinationPath = "${downloadPathDir}/${downloadName}",
        )
      )
    )

    val fontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadVdIcon(symbolConfiguration, symbolName)

    val updatedFontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }
}
