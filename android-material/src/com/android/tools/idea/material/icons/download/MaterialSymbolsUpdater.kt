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
import com.android.tools.idea.material.icons.common.MaterialSymbolsFontUrlProvider
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.download.DownloadableFileService
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LOG = Logger.getInstance(MaterialSymbolsUpdater::class.java)

/** Class to aggregate download methods used in Material Symbols */
class MaterialSymbolsUpdater {
  companion object {
    // incomplete=true because Material has not published a "complete" set of icons in a few years,
    // and no Material Symbols are published in the last complete set
    private const val METADATA_DOWNLOAD_URL =
      "https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true"
    private const val DOWNLOADED_METADATA_FILE_NAME = "icons_metadata_temp.txt"
    private const val FONT_FILE_DOWNLOADER_NAME = "MaterialSymbolsFont"
    private const val METADATA_DOWNLOADER_NAME = "MaterialSymbolsMetadata"
    private const val SYMBOL_VD_DOWNLOADER_NAME = "PickedMaterialSymbol"
    private const val FONT_EXTENSION = ".ttf"

    /**
     * Handles the download of the variable font TTF files used in Material Symbols rendering
     *
     * @param url The download [URL] for the Material Symbols font
     * @param type The [Symbols] type that corresponds to the font pack rquired
     */
    @Slow
    fun downloadFontFiles(url: URL, type: Symbols) {
      val folder = MaterialSymbolsFontUrlProvider.getLocalFontDirectoryFile(type) ?: return
      val fileName = type.remoteFileName
      downloadAndMove(
        url.toString(),
        fileName,
        type.localName + FONT_EXTENSION,
        folder,
        FONT_FILE_DOWNLOADER_NAME,
      )
    }

    /** Downloads the metadata file for the Material Symbols */
    @Slow
    fun downloadMetadataFile() {
      val folder = MaterialIconsUtils.getIconsSdkTargetPath() ?: return
      downloadAndMove(
        METADATA_DOWNLOAD_URL,
        DOWNLOADED_METADATA_FILE_NAME,
        METADATA_FILE_NAME,
        folder,
        METADATA_DOWNLOADER_NAME,
      )
    }

    /**
     * Downloads the [com.android.ide.common.vectordrawable.VdIcon] for the specified Material
     * Symbol
     *
     * @param symbolConfiguration The [SymbolConfiguration] that defines the visual properties of
     *   the Material Symbol to be downloaded
     * @param symbolName The name of the Material Symbol to be downloaded
     */
    @Slow
    fun downloadVdIcon(symbolConfiguration: SymbolConfiguration, symbolName: String) {
      val folder =
        MaterialIconsUtils.getIconsSdkTargetPath()
          ?.resolve("${symbolConfiguration.type.localName}/${symbolName}") ?: return
      val fileName = symbolConfiguration.toFileName(symbolName)
      val remoteUrl = symbolConfiguration.toUrlString(symbolName)
      downloadAndMove(remoteUrl, "$fileName.tmp", fileName, folder, SYMBOL_VD_DOWNLOADER_NAME)
    }

    /**
     * Ensures a safe download for the required resources, by downloading to a temporary file, then
     * moving do the final one to avoid partial downloads
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

        val fileDescription =
          listOf(downloadService.createFileDescription(downloadUrl, tempFileName))

        val downloader = downloadService.createDownloader(fileDescription, downloaderName)
        val downloadedFile = downloader.download(downloadFolder).first().first

        Files.move(
          downloadedFile.toPath(),
          downloadedFile.parentFile.resolve(finalFileName).toPath(),
          StandardCopyOption.REPLACE_EXISTING,
        )
        downloadedFile.delete()
      } catch (e: Throwable) {
        LOG.warn("Download failed for $finalFileName with error: $e")
      }
    }
  }
}
