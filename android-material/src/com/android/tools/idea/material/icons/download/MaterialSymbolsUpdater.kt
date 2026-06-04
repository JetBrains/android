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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.material.icons.common.MaterialSymbolsUrlProvider
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.download.DownloadableFileService
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

private val LOG = Logger.getInstance(MaterialSymbolsUpdater::class.java)
private val CSS_FONT_URL_REGEX = """url\(['"]?([^'"()]+)['"]?\)""".toRegex()

/** Class to aggregate download methods used in Material Symbols */
class MaterialSymbolsUpdater {
  companion object {
    // incomplete=true because Material has not published a "complete" set of icons in a few years,
    // and no Material Symbols are published in the last complete set
    private const val METADATA_DOWNLOAD_URL = "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    private const val DOWNLOADED_METADATA_FILE_NAME = "icons_metadata_temp.txt"
    private const val FONT_FILE_DOWNLOADER_NAME = "MaterialSymbolsFont"
    private const val METADATA_DOWNLOADER_NAME = "MaterialSymbolsMetadata"
    private const val SYMBOL_VD_DOWNLOADER_NAME = "PickedMaterialSymbol"
    private const val FONT_EXTENSION = ".ttf"
    private const val GOOGLE_FONTS_CSS_INDICATOR = "fonts.googleapis.com/css"

    private fun extractFontUrlFromCss(cssContent: String): String? {
      // Find all URLs in url(...) blocks
      val matches = CSS_FONT_URL_REGEX.findAll(cssContent).map { it.groupValues[1] }.toList()
      if (matches.isEmpty()) return null

      // If there are multiple matches, prefer the one ending in .ttf or having ttf
      val ttfMatch = matches.find { it.endsWith(FONT_EXTENSION) || it.contains(FONT_EXTENSION) }
      if (ttfMatch != null) {
        if (matches.size > 1) {
          LOG.warn("Multiple font URLs found in CSS: $matches. Selecting the TTF match: $ttfMatch")
        }
        return ttfMatch
      }

      if (matches.size > 1) {
        LOG.warn("Multiple font URLs found in CSS: $matches. Selecting the first one: ${matches.first()}")
      }
      return matches.first()
    }

    /**
     * Handles the download of the variable font TTF files used in Material Symbols rendering.
     *
     * If the remote URL points to a Google Fonts CSS stylesheet (containing "fonts.googleapis.com/css"), it indicates an indirect font
     * load. We first download the CSS file, extract the actual TTF font file URL from the CSS, and then download the font file from that
     * extracted URL. Otherwise, we download the font file directly from the remote URL.
     *
     * @param type The [Symbols] type that corresponds to the font pack required
     */
    @Slow
    fun downloadFontFiles(type: Symbols, materialSymbolsUrlProvider: MaterialSymbolsUrlProvider) {
      val url = materialSymbolsUrlProvider.getRemoteFontUrl(type)
      val folder = materialSymbolsUrlProvider.getLocalFontDirectoryFile(type) ?: return
      val fileName = type.remoteFileName
      val finalFileName = type.localName + FONT_EXTENSION

      if (url.toString().contains(GOOGLE_FONTS_CSS_INDICATOR)) {
        downloadIndirectFontFromCss(url.toString(), fileName, finalFileName, folder)
      } else {
        downloadAndMove(url.toString(), fileName, finalFileName, folder, FONT_FILE_DOWNLOADER_NAME)
      }
    }

    /**
     * The Material Symbols fonts are served to web via a CSS. We use that to download the font. We used to use a github raw endpoint that
     * contained the ttf but those are frequently out of sync with the latest symbols exposed in fonts.google.com.
     */
    @Slow
    private fun downloadIndirectFontFromCss(cssUrl: String, tempFileName: String, finalFileName: String, folder: File) {
      val tempCssFileName = "temp_font_css_${UUID.randomUUID()}.css"
      downloadAndMove(cssUrl, "$tempCssFileName.tmp", tempCssFileName, folder, FONT_FILE_DOWNLOADER_NAME)
      val cssFile = folder.resolve(tempCssFileName)
      if (cssFile.exists()) {
        try {
          val cssContent = cssFile.readText()
          val fontUrl = extractFontUrlFromCss(cssContent)
          if (fontUrl != null) {
            downloadAndMove(fontUrl, tempFileName, finalFileName, folder, FONT_FILE_DOWNLOADER_NAME)
          } else {
            LOG.warn("Could not find font URL in CSS: $cssContent")
          }
        } catch (e: Exception) {
          LOG.warn("Failed to read/parse CSS file: $e")
        } finally {
          cssFile.delete()
        }
      } else {
        LOG.warn("Failed to download CSS from $cssUrl")
      }
    }

    /** Downloads the metadata file for the Material Symbols */
    @Slow
    fun downloadMetadataFile(materialSymbolsUrlProvider: MaterialSymbolsUrlProvider) {
      val folder = materialSymbolsUrlProvider.getLocalSymbolsPath() ?: return
      downloadAndMove(METADATA_DOWNLOAD_URL, DOWNLOADED_METADATA_FILE_NAME, METADATA_FILE_NAME, folder, METADATA_DOWNLOADER_NAME)
    }

    /**
     * Downloads the [com.android.ide.common.vectordrawable.VdIcon] for the specified Material Symbol
     *
     * @param symbolConfiguration The [SymbolConfiguration] that defines the visual properties of the Material Symbol to be downloaded
     * @param symbolName The name of the Material Symbol to be downloaded
     */
    @Slow
    fun downloadVdIcon(
      symbolConfiguration: SymbolConfiguration,
      symbolName: String,
      materialSymbolsUrlProvider: MaterialSymbolsUrlProvider,
    ) {
      val folder =
        materialSymbolsUrlProvider.getLocalSymbolsPath()?.resolve("${symbolConfiguration.type.localName}/${symbolName}") ?: return
      val fileName = symbolConfiguration.toFileName(symbolName)
      val remoteUrl = symbolConfiguration.toUrlString(symbolName)
      downloadAndMove(remoteUrl, "$fileName.tmp", fileName, folder, SYMBOL_VD_DOWNLOADER_NAME)
    }

    /**
     * Ensures a safe download for the required resources, by downloading to a temporary file, then moving do the final one to avoid partial
     * downloads
     *
     * @param downloadUrl [String] determining the remote URL the resource should be downloaded from
     * @param tempFileName temporary file name to download the resource to
     * @param finalFileName final file name after overwriting the existing file
     * @param downloadFolder [File] determining where the resource should be downloaded
     * @param downloaderName name of the downloader
     */
    private fun downloadAndMove(
      downloadUrl: String,
      tempFileName: String,
      finalFileName: String,
      downloadFolder: File,
      downloaderName: String,
    ) {
      try {
        val downloadService = DownloadableFileService.getInstance()

        val fileDescription = listOf(downloadService.createFileDescription(downloadUrl, tempFileName))

        val downloader = downloadService.createDownloader(fileDescription, downloaderName)
        val downloadedFile = downloader.download(downloadFolder).first().first

        Files.move(downloadedFile.toPath(), downloadedFile.parentFile.resolve(finalFileName).toPath(), StandardCopyOption.REPLACE_EXISTING)
        downloadedFile.delete()
      } catch (e: Throwable) {
        LOG.warn("Download failed for $finalFileName with error: $e")
      }
    }
  }
}
