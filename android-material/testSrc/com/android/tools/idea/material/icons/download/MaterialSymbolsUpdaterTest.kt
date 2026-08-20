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
import com.android.tools.idea.material.icons.common.SymbolsSdkUrlProvider
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

private const val OLD_FILE_CONTENT = "old" // For this test, it doesn't matter if it's a valid Vector Drawable file
private const val NEW_FILE_CONTENT = "new"

private data class FakeDownloadWithContent(val url: String, val downloadPath: String, val destinationPath: String, val content: String)

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
      val descriptor = DownloadableFileDescriptionImpl(it.url, FileUtil.toSystemDependentName(it.destinationPath), "tmp")

      whenever(mockDownloadableFileService.createFileDescription(it.url, it.downloadPath)).thenReturn(descriptor)

      whenever(mockDownloader.download(Mockito.any())).thenAnswer {
        val downloadedFile =
          downloadDir
            .resolve(descriptor.defaultFileName)
            .apply {
              parent.createDirectories()
              writeText(NEW_FILE_CONTENT)
            }
            .toFile()
        return@thenAnswer listOf(Pair<File, DownloadableFileDescription>(downloadedFile, descriptor))
      }
    }

    whenever(
        mockDownloadableFileService.createDownloader(
          Mockito.any(),
          Mockito.argThat { listOf("MaterialSymbolsFont", "MaterialSymbolsMetadata", "PickedMaterialSymbol").contains(it) },
        )
      )
      .thenReturn(mockDownloader)
  }

  private fun mockDownloadServiceWithContent(downloads: List<FakeDownloadWithContent>) {
    val mockDownloadableFileService = Mockito.mock(DownloadableFileService::class.java)
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(
        DownloadableFileService::class.java,
        mockDownloadableFileService,
        projectRule.fixture.testRootDisposable,
      )

    downloads.forEach { download ->
      val descriptor = DownloadableFileDescriptionImpl(download.url, FileUtil.toSystemDependentName(download.destinationPath), "tmp")
      if (download.downloadPath == "temp_font_css.css.tmp") {
        whenever(mockDownloadableFileService.createFileDescription(Mockito.eq(download.url), Mockito.anyString())).thenReturn(descriptor)
      } else {
        whenever(mockDownloadableFileService.createFileDescription(download.url, download.downloadPath)).thenReturn(descriptor)
      }
    }

    whenever(mockDownloadableFileService.createDownloader(Mockito.any(), Mockito.anyString())).thenAnswer { invocation ->
      val descriptions = invocation.arguments[0] as List<DownloadableFileDescription>
      val downloader = Mockito.mock(FileDownloader::class.java)
      whenever(downloader.download(Mockito.any())).thenAnswer { downloadInvocation ->
        val downloadFolder = downloadInvocation.arguments[0] as File
        val results = descriptions.map { desc ->
          val matchingDownload = downloads.find { it.url == desc.downloadUrl }
          val content = matchingDownload?.content ?: NEW_FILE_CONTENT
          val fileName = matchingDownload?.downloadPath ?: desc.defaultFileName
          val downloadedFile =
            downloadFolder
              .toPath()
              .resolve(fileName)
              .apply {
                parent.createDirectories()
                writeText(content)
              }
              .toFile()
          Pair<File, DownloadableFileDescription>(downloadedFile, desc)
        }
        return@thenAnswer results
      }
      return@thenAnswer downloader
    }
  }

  @Before
  fun setup() {
    testDirectory = createTempDirectory(javaClass.simpleName)
    downloadDir = testDirectory.resolve("downloads")
  }

  @Test
  fun updateFontFile() {
    val testStyle = Symbols.OUTLINED

    val cssUrl = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-25..200"
    val fontUrl =
      "https://fonts.gstatic.com/s/materialsymbolsoutlined/v344/kJF1BvYX7BgnkSrUwT8OhrdQw4oELdPIeeII9v6oDMzByHX9rA6RzaxHMPdY43zj-jCxv3fzvRNU22ZXGJpEpjC_1v-p_4MrImHCIJIZrDCvHOem.ttf"
    val cssContent =
      """
      @font-face {
        font-family: 'Material Symbols Outlined';
        font-style: normal;
        font-weight: 400;
        src: url($fontUrl) format('truetype');
      }

      .material-symbols-outlined {
        font-family: 'Material Symbols Outlined';
        font-weight: normal;
        font-style: normal;
        font-size: 24px;
        line-height: 1;
        letter-spacing: normal;
        text-transform: none;
        display: inline-block;
        white-space: nowrap;
        word-wrap: normal;
        direction: ltr;
      }
      """
        .trimIndent()

    val urlProvider = SymbolsSdkUrlProvider()
    val fontFile = urlProvider.getLocalFontFile(testStyle)!!
    fontFile.parentFile.mkdirs()
    fontFile.writeText(OLD_FILE_CONTENT)

    val downloadPathDir = "variablefont/${testStyle.localName}"

    mockDownloadServiceWithContent(
      listOf(
        FakeDownloadWithContent(
          url = cssUrl,
          downloadPath = "temp_font_css.css.tmp",
          destinationPath = "${downloadPathDir}/temp_font_css.css.tmp",
          content = cssContent,
        ),
        FakeDownloadWithContent(
          url = fontUrl,
          downloadPath = testStyle.remoteFileName,
          destinationPath = "${downloadPathDir}/${testStyle.remoteFileName}",
          content = NEW_FILE_CONTENT,
        ),
      )
    )

    assertEquals(OLD_FILE_CONTENT, fontFile.readText())

    MaterialSymbolsUpdater.downloadFontFiles(testStyle, urlProvider)

    assertThat(fontFile).exists()
    assertEquals(NEW_FILE_CONTENT, fontFile.readText())
  }

  @Test
  fun downloadFontFile() {
    val testStyle = Symbols.OUTLINED

    val cssUrl = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-25..200"
    val fontUrl =
      "https://fonts.gstatic.com/s/materialsymbolsoutlined/v344/kJF1BvYX7BgnkSrUwT8OhrdQw4oELdPIeeII9v6oDMzByHX9rA6RzaxHMPdY43zj-jCxv3fzvRNU22ZXGJpEpjC_1v-p_4MrImHCIJIZrDCvHOem.ttf"
    val cssContent =
      """
      @font-face {
        font-family: 'Material Symbols Outlined';
        font-style: normal;
        font-weight: 400;
        src: url($fontUrl) format('truetype');
      }

      .material-symbols-outlined {
        font-family: 'Material Symbols Outlined';
        font-weight: normal;
        font-style: normal;
        font-size: 24px;
        line-height: 1;
        letter-spacing: normal;
        text-transform: none;
        display: inline-block;
        white-space: nowrap;
        word-wrap: normal;
        direction: ltr;
      }
      """
        .trimIndent()

    val urlProvider = SymbolsSdkUrlProvider()
    val fontFile = urlProvider.getLocalFontFile(testStyle)!!
    fontFile.parentFile.mkdirs()
    fontFile.writeText(OLD_FILE_CONTENT)

    val downloadPathDir = "variablefont/${testStyle.localName}"

    mockDownloadServiceWithContent(
      listOf(
        FakeDownloadWithContent(
          url = cssUrl,
          downloadPath = "temp_font_css.css.tmp",
          destinationPath = "${downloadPathDir}/temp_font_css.css.tmp",
          content = cssContent,
        ),
        FakeDownloadWithContent(
          url = fontUrl,
          downloadPath = testStyle.remoteFileName,
          destinationPath = "${downloadPathDir}/${testStyle.remoteFileName}",
          content = NEW_FILE_CONTENT,
        ),
      )
    )

    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadFontFiles(testStyle, urlProvider)

    assertThat(fontFile).exists()
    assertEquals(NEW_FILE_CONTENT, fontFile.readText())
  }

  @Test
  fun downloadMetadataFile() {
    val downloadUrl = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    val tempDownloadName = "icons_metadata_temp.txt"
    val finalDownloadName = "icons_metadata.txt"

    downloadDir.apply { createDirectories() }.resolve(tempDownloadName).writeText(OLD_FILE_CONTENT)

    mockDownloadService(listOf(FakeDownload(url = downloadUrl, downloadPath = tempDownloadName, destinationPath = tempDownloadName)))

    val fontFile = downloadDir.resolve(tempDownloadName)
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())
    fontFile.delete()

    MaterialSymbolsUpdater.downloadMetadataFile(SymbolsSdkUrlProvider())

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

    mockDownloadService(listOf(FakeDownload(url = downloadUrl, downloadPath = tempDownloadName, destinationPath = tempDownloadName)))

    val fontFile = downloadDir.resolve(tempDownloadName)
    assertEquals(OLD_FILE_CONTENT, fontFile.readText())

    MaterialSymbolsUpdater.downloadMetadataFile(SymbolsSdkUrlProvider())

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

    downloadDir.resolve(downloadPathDir).apply { createDirectories() }.resolve(downloadName).writeText(OLD_FILE_CONTENT)

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

    MaterialSymbolsUpdater.downloadVdIcon(symbolConfiguration, symbolName, SymbolsSdkUrlProvider())

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

    downloadDir.resolve(downloadPathDir).apply { createDirectories() }.resolve(downloadName).writeText(OLD_FILE_CONTENT)

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

    MaterialSymbolsUpdater.downloadVdIcon(symbolConfiguration, symbolName, SymbolsSdkUrlProvider())

    val updatedFontFile = downloadDir.resolve("${downloadPathDir}/${downloadName}")
    assertThat(updatedFontFile).exists()
    assertEquals(NEW_FILE_CONTENT, updatedFontFile.readText())
  }

  @Test
  fun testUrlStringChangesWithFilledAndOtherConfigurations() {
    val unFilledConfig = SymbolConfiguration(Symbols.OUTLINED, 400, 0, 24, filled = false)
    val filledConfig = SymbolConfiguration(Symbols.OUTLINED, 400, 0, 24, filled = true)
    val customConfig = SymbolConfiguration(Symbols.OUTLINED, 700, 200, 48, filled = true)

    assertEquals(
      "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/favorite/default/24px.xml",
      unFilledConfig.toUrlString("favorite"),
    )
    assertEquals(
      "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/favorite/fill1/24px.xml",
      filledConfig.toUrlString("favorite"),
    )
    assertEquals(
      "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/favorite/wght700grad200fill1/48px.xml",
      customConfig.toUrlString("favorite"),
    )

    assertEquals("favorite_24px.xml", unFilledConfig.toFileName("favorite"))
    assertEquals("favorite_fill1_24px.xml", filledConfig.toFileName("favorite"))
    assertEquals("favorite_wght700grad200fill1_48px.xml", customConfig.toFileName("favorite"))
  }
}
