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
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getIconFileNameWithoutExtension
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.writeText

private interface MaterialIconsUpdater

private val log = Logger.getInstance(MaterialIconsUpdater::class.java)

/**
 * Updates material icons at the [targetDir]. Deletes and or downloads new icons based on the differences between [newMetadata] and
 * [existingMetadata].
 *
 * For each icon that needs to be deleted, all variants of the icons are downloaded and then the metadata file is updated.
 *
 * For each icon that needs to be downloaded, all variants of the icon are first downloaded and then the metadata file is updated, once
 * the metadata file is updated, the download for that icon is considered finished.
 *
 * Checks for a progress indicator between each delete/download operation. So the update process may be interrupted and continued later.
 */
@Slow
fun updateIconsAtDir(existingMetadata: MaterialIconsMetadata, newMetadata: MaterialIconsMetadata, targetDir: Path) {
  require(targetDir.isDirectory())

  // The metadata builder should reflect the current status of the metadata during the process, so deletions or additions of icons should be
  // updated here
  val metadataBuilder = MaterialIconsMetadataBuilder(
    host = existingMetadata.host,
    urlPattern = existingMetadata.urlPattern,
    families = existingMetadata.families
  )
  existingMetadata.icons.forEach(metadataBuilder::addIconMetadata)

  val updateData = getIconsUpdateData(existingMetadata, newMetadata)

  log.info("Will attempt to delete ${updateData.iconsToDelete.size} icons")
  updateData.iconsToDelete.forEach { iconMetadata ->
    ProgressManager.getInstance().progressIndicator?.checkCanceled()
    try {
      deleteIconStyles(existingMetadata, targetDir, iconMetadata)

      // Only update the metadata if deletion doesn't fail, so that we can try again next time.
      metadataBuilder.removeIconMetadata(iconMetadata)
      updateSavedMetadata(targetDir, metadataBuilder)
    }
    catch (e: Exception) {
      log.warn("Error while deleting '${iconMetadata.name}'", e)
    }
  }

  log.info("Will attempt to download ${updateData.iconsToDownload.size} icons")
  updateData.iconsToDownload.forEach { iconMetadata ->
    ProgressManager.getInstance().progressIndicator?.checkCanceled()
    try {
      downloadIconStyles(existingMetadata, targetDir, iconMetadata)

      // Only update the metadata if download doesn't fail, so that we can try again next time.
      metadataBuilder.addIconMetadata(iconMetadata)
      updateSavedMetadata(targetDir, metadataBuilder)
    }
    catch (e: Exception) {
      log.warn("Error while downloading '${iconMetadata.name}'", e)
    }
  }
}

/**
 * Deletes all variants of the icon given by [iconMetadata] at the [targetDir].
 */
private fun deleteIconStyles(existingMetadata: MaterialIconsMetadata, targetDir: Path, iconMetadata: MaterialMetadataIcon) {
  existingMetadata.families.forEach { style ->
    val styleDir = targetDir.resolve(style.toDirFormat())
    if (!styleDir.exists()) {
      throw IllegalStateException("Can't find style directory: ${styleDir.name}")
    }
    val iconDir = styleDir.resolve(iconMetadata.name)
    if (!iconDir.exists()) {
      throw IllegalStateException("Can't find icon file of: ${iconMetadata.name}, of style: $style")
    }
    iconDir.delete(recursively = true)
  }
}

/**
 * Downloads all styles for the given [iconMetadata] to the [targetDir].
 *
 * Deletes existing files of [iconMetadata] before downloading.
 */
private fun downloadIconStyles(existingMetadata: MaterialIconsMetadata, targetDir: Path, iconMetadata: MaterialMetadataIcon) {
  val fileDescriptions = existingMetadata.families.map { style ->
    targetDir.resolve(style.toDirFormat()).createDirectories()
    createMaterialIconFileDescription(existingMetadata, iconMetadata, style)
  }
  val downloader = DownloadableFileService.getInstance().createDownloader(fileDescriptions, "Material Icons")
  val downloaded = downloader.download(targetDir.toFile()).map { it.first }
  val renamedFiles = renameDownloadedFiles(downloaded)
  cleanUpIconDirectories(renamedFiles)
}

/**
 * Returns a [DownloadableFileDescription] using the url pattern from [MaterialIconsMetadata.urlPattern].
 */
private fun createMaterialIconFileDescription(existingMetadata: MaterialIconsMetadata, iconMetadata: MaterialMetadataIcon, style: String): DownloadableFileDescription {
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

/**
 * Returns an object with the icons that should be removed & downloaded.
 *
 * Icons to delete are those in [oldMetadata] that are not present in [newMetadata].
 *
 * Icons to download are those in [newMetadata] that are not present in [oldMetadata] or that have a higher [MaterialMetadataIcon.version].
 */
private fun getIconsUpdateData(oldMetadata: MaterialIconsMetadata, newMetadata: MaterialIconsMetadata): IconsUpdateData {
  val existingIconsMap = oldMetadata.icons.associateBy { it.name }
  val newIconsMap = newMetadata.icons.associateBy { it.name }


  val iconsToDelete = arrayListOf<MaterialMetadataIcon>()
  oldMetadata.icons.forEach { icon ->
    if (!newIconsMap.contains(icon.name)) {
      iconsToDelete.add(icon)
    }
  }

  val iconsToDownload = newMetadata.icons.filter {
    existingIconsMap[it.name] == null || existingIconsMap[it.name]!!.version < it.version
  }

  return IconsUpdateData(iconsToDelete, iconsToDownload)
}

/**
 * Updates the metadata file with the contents of [metadataBuilder] in the [targetDir].
 */
private fun updateSavedMetadata(targetDir: Path, metadataBuilder: MaterialIconsMetadataBuilder) {
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

private data class IconsUpdateData(
  val iconsToDelete: List<MaterialMetadataIcon>,
  val iconsToDownload: List<MaterialMetadataIcon>
)