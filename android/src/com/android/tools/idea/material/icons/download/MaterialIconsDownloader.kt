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

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadataBuilder
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LOG = Logger.getInstance(MaterialIconsDownloader::class.java)

/**
 * Downloads the Vector Drawables files for new material icons.
 *
 * @param existingMetadata The [MaterialIconsMetadata] for all existing icons.
 * @param newMetadata A more recent [MaterialIconsMetadata] from which additional or more recent (with a higher
 * [MaterialMetadataIcon.version]) icons will be downloaded.
 */
class MaterialIconsDownloader(private val existingMetadata: MaterialIconsMetadata,
                              newMetadata: MaterialIconsMetadata) {
  private val iconsToDownload = getIconsToDownload(existingMetadata, newMetadata)
  // TODO: Consider making this class a single instance that keeps track of downloads performed for each directory (File) to guarantee that
  //  simultaneous downloads cannot be performed on the same directory.

  /**
   * Downloads any new icons to the given [targetDir].
   *
   * For each icon that needs to be downloaded, all variants of the icon are first downloaded and then the metadata file is updated, once
   * the metadata file is updated, the download for that icon is considered finished.
   *
   * Checks for a progress indicator between downloads, which may cancel any remaining downloads.
   */
  @Slow
  fun downloadTo(targetDir: File) {
    require(targetDir.isDirectory)
    val metadataBuilder = MaterialIconsMetadataBuilder(host = existingMetadata.host,
                                                       urlPattern = existingMetadata.urlPattern,
                                                       families = existingMetadata.families)
    existingMetadata.icons.forEach(metadataBuilder::addIconMetadata)
    iconsToDownload.forEach { iconMetadata ->
      ProgressManager.getInstance().progressIndicator?.checkCanceled()
      try {
        downloadIconStyles(targetDir, iconMetadata)
        updateSavedMetadata(targetDir, iconMetadata, metadataBuilder)
      }
      catch (e: Exception) {
        // Don't register failed downloads in the metadata, so that they can be downloaded next time.
        LOG.warn("Error while downloading '${iconMetadata.name}'")
      }
    }
  }

  /**
   * Downloads all styles for the given [iconMetadata] to the [targetDir].
   *
   * Deletes existing files of [iconMetadata] before downloading.
   */
  private fun downloadIconStyles(targetDir: File, iconMetadata: MaterialMetadataIcon) {
    val fileDescriptions = existingMetadata.families.map { style ->
      targetDir.resolve(style.toDirFormat()).mkdir()
      createMaterialIconFileDescription(iconMetadata, style)
    }
    val downloader = DownloadableFileService.getInstance().createDownloader(fileDescriptions, "Material Icons")
    val downloaded = downloader.download(targetDir).map { it.first }
    val renamedFiles = renameDownloadedFiles(downloaded)
    cleanUpIconDirectories(renamedFiles)
  }

  /**
   * Returns a [DownloadableFileDescription] using the url pattern from [MaterialIconsMetadata.urlPattern].
   */
  private fun createMaterialIconFileDescription(iconMetadata: MaterialMetadataIcon, style: String): DownloadableFileDescription {
    val styleDirName = style.toDirFormat()
    val iconName = iconMetadata.name
    val host = existingMetadata.host
    val pattern = existingMetadata.urlPattern
      .replace("{family}", styleDirName)
      .replace("{icon}", iconName)
      .replace("{version}", iconMetadata.version.toString())
      .replace("{asset}", "24px.xml")
    val downloadUrl = "https://%1s%2s".format(host, pattern)
    val fileName =
      styleDirName + File.separatorChar + iconName + File.separatorChar + getIconFileNameWithoutExtension(iconName, styleDirName) + ".tmp"
    return DownloadableFileService.getInstance().createFileDescription(downloadUrl, fileName)
  }
}

/**
 * Returns a list of all icons that should be downloaded.
 *
 * This is based from any icons in [newMetadata] that are not present in [oldMetadata] or that have a higher [MaterialMetadataIcon.version].
 */
private fun getIconsToDownload(oldMetadata: MaterialIconsMetadata, newMetadata: MaterialIconsMetadata): List<MaterialMetadataIcon> {
  val existingIconsMap = HashMap<String, MaterialMetadataIcon>()
  oldMetadata.icons.forEach {
    existingIconsMap[it.name] = it
  }

  return newMetadata.icons.filter {
    !existingIconsMap.contains(it.name) || existingIconsMap[it.name]!!.version < it.version
  }
}

/**
 * Adds the [iconMetadata] to the [metadataBuilder] and updates the metadata file with the new contents in the [targetDir].
 */
private fun updateSavedMetadata(targetDir: File, iconMetadata: MaterialMetadataIcon, metadataBuilder: MaterialIconsMetadataBuilder) {
  metadataBuilder.addIconMetadata(iconMetadata)
  targetDir.resolve(METADATA_FILE_NAME).writeText(MaterialIconsMetadata.parse(metadataBuilder.build()))
}

/**
 * Removes any remaining files in the directories of the downloaded icons, so that there's only the downloaded file in each of the icon
 * directories.
 *
 * E.g. if after the download .../my_icon/ contains 'old_icon.xml' and 'new_icon.xml', 'old_icon.xml' is removed.
 */
private fun cleanUpIconDirectories(downloadedFiles: List<File>) {
  downloadedFiles.forEach { downloadedFile ->
    val iconDirectory = downloadedFile.parentFile
    val filesToCleanUp = iconDirectory.listFiles()?.filter { it.name != downloadedFile.name } ?: emptyList()
    filesToCleanUp.forEach {
      if (!it.delete()) {
        // Only one file is expected in the icon directory, so not being able to delete old files could be an issue
        throw IllegalStateException("Unable to delete file: ${it.name}")
      }
    }
  }
}

/**
 * Renames the downloaded files to the expected naming scheme for material icons.
 *
 * @return The list of files with their new name
 */
private fun renameDownloadedFiles(downloadedFiles: List<File>): List<File> {
  return downloadedFiles.map { downloadedFile ->
    val iconDir = downloadedFile.parentFile
    val iconName = iconDir.name
    val styleDirName = iconDir.parentFile.name
    val newIconFileName = getIconFileNameWithoutExtension(iconName, styleDirName) + SdkConstants.DOT_XML
    val destFile = iconDir.resolve(newIconFileName)
    return@map Files.move(downloadedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile()
  }
}

/**
 * Returns the expected file name for material icons given the [iconName] and the [styleDirName].
 *
 * E.g. For 'android' of 'materialiconsrounded' returns 'rounded_android_24.xml'
 */
private fun getIconFileNameWithoutExtension(iconName: String, styleDirName: String): String {
  // TODO(141628234): Use a consistent logic with VdIcon.getDisplayName()
  val family = styleDirName.substringAfter("materialicons")
  val familyPrefix = when (family) {
    "" -> "baseline_"
    "outlined" -> "outline_"
    else -> family + "_"
  }
  return familyPrefix + iconName + "_24"
}