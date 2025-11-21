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
package com.android.tools.idea.material.icons

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.common.BundledMetadataUrlProvider
import com.android.tools.idea.material.icons.common.MaterialSymbolsFontUrlProvider
import com.android.tools.idea.material.icons.common.SdkMaterialIconsUrlProvider
import com.android.tools.idea.material.icons.common.SdkMetadataUrlProvider
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.material.icons.download.MaterialSymbolsUpdater
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * Class providing the loading of Metadata and [VdIcon] for Material Symbols, and ensures the font
 * files required for rendering exist
 */
class MaterialSymbolsLoader {
  companion object {

    private const val METADATA_REFRESH_INTERVAL_DAYS = 3

    /**
     * Function that ensures Material Symbols Metadata and font files are downloaded and up-to-date
     *
     * @param scope The [CoroutineScope] for launching all downloads
     * @param forceMetadataDownload If true, will update the metadata regardless of automated
     *   internal checks
     * @param callback Callback function to be called when metadata has been provided
     */
    suspend fun getMaterialSymbolsFontsAndMetadata(
      scope: CoroutineScope,
      forceMetadataDownload: Boolean,
      callback: @UiThread (MaterialIconsMetadata) -> Unit,
    ) {
      val symbolDownloadsToStart =
        Symbols.entries.filter { !MaterialSymbolsFontUrlProvider.hasFontPathInSdk(it) }
      val downloads: MutableList<Job> =
        symbolDownloadsToStart.map { scope.launch { downloadFonts(it) } }.toMutableList()

      val metadataParsingResult = tryToParseMetadata(callback)
      if (!metadataParsingResult || forceMetadataDownload) {
        downloads.add(scope.launch { downloadMaterialMetadata() })
      }

      downloads.joinAll()

      if (!metadataParsingResult || forceMetadataDownload) {
        tryToParseMetadata(callback)
      }
    }

    @WorkerThread
    private fun downloadFonts(type: Symbols) {
      MaterialSymbolsUpdater.downloadFontFiles(
        MaterialSymbolsFontUrlProvider.getRemoteFontUrl(type),
        type,
      )
    }

    /**
     * Tries to locate and parse [MaterialIconsMetadata] in the Sdk, on failure falls back
     * temporarily to the metadata bundled with Studio
     *
     * @param callback Callback function to be called when metadata has been successfully parsed
     * @return true if parsing the file in the Sdk succeeded, false otherwise
     */
    private fun tryToParseMetadata(callback: (MaterialIconsMetadata) -> Unit): Boolean {
      val metadataUrl = SdkMetadataUrlProvider().getMetadataUrl() ?: return false
      val shouldRefresh = checkAgeForRefresh(Path.of(metadataUrl.toURI()).toFile(), METADATA_REFRESH_INTERVAL_DAYS)
      val metadataParseResult = metadataUrl.let { MaterialIconsMetadata.parse(it) }

      if (metadataParseResult.isSuccess) {
        callback(metadataParseResult.getOrDefault(MaterialIconsMetadata.EMPTY))
        return !shouldRefresh
      }

      val fallbackMetadata =
        BundledMetadataUrlProvider().getMetadataUrl()?.let { MaterialIconsMetadata.parse(it) }
          ?: Result.failure(RuntimeException("Failed to parse bundled metadata"))

      if (fallbackMetadata.isSuccess) {
        callback(fallbackMetadata.getOrDefault(MaterialIconsMetadata.EMPTY))
      }

      return false
    }

    private fun checkAgeForRefresh(file: File, days: Int): Boolean {
      val lastModifiedInstant = Instant.ofEpochMilli(file.lastModified())
      val now = Instant.now()
      val durationSinceModification = Duration.between(lastModifiedInstant, now)
      return durationSinceModification.toDays() > days
    }

    @WorkerThread
    private fun downloadMaterialMetadata() {
      MaterialSymbolsUpdater.downloadMetadataFile()
    }

    /**
     * Loads or downloads if missing the [VdIcon] representation of the requested Material Symbol
     *
     * @param symbolConfiguration [SymbolConfiguration] detailing the visual properties of the
     *   Material Symbol
     * @param iconMetadata The [MaterialMetadataIcon] of the Material Symbol to be loaded
     * @param iconsMetadata The [MaterialIconsMetadata] of the entire list of Material Symbols,
     *   required for instantiating the [MaterialVdIconsLoader]
     * @return [VdIcon] of the corresponding Material Symbol
     */
    fun loadVdIcon(
      symbolConfiguration: SymbolConfiguration,
      iconMetadata: MaterialMetadataIcon,
      iconsMetadata: MaterialIconsMetadata,
    ): VdIcon {
      val iconFileName = symbolConfiguration.toFileName(iconMetadata.name)
      val loader = MaterialVdIconsLoader(iconsMetadata, SdkMaterialIconsUrlProvider())
      val loadedIcon =
        loader.loadVdIcon(symbolConfiguration.type.localName, iconMetadata.name, iconFileName)

      if (loadedIcon != null) {
        return loadedIcon
      }

      MaterialSymbolsUpdater.downloadVdIcon(symbolConfiguration, iconMetadata.name)

      return loader.loadVdIcon(
        symbolConfiguration.type.localName,
        iconMetadata.name,
        iconFileName,
      )!!
    }
  }
}
