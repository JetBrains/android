/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.io.CancellableFileIo
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadataBuilder
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getIconFileNameWithoutExtension
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.name

private interface MaterialIconsUpdater

private val log = Logger.getInstance(MaterialIconsUpdater::class.java)

/**
 * Updates material icons at the [targetDir].
 *
 * Icon directories not referenced by [existingMetadata] may be deleted.
 *
 * Based on the contents of [newMetadata], new icon files may be downloaded or their reference in the metadata file may be removed.
 *
 * Icons that are both part of the [existingMetadata] and [newMetadata] will be checked for corruption (for example, the icons being
 * deleted). If they are not found, they will be re-downloaded. The [iconsUrlProvider] is used to check the presence of the icons.
 *
 * For each icon that needs to be downloaded, all variants of the icon are first downloaded and then the metadata file is updated, once
 * the metadata file is updated, the download for that icon is considered finished.
 *
 * Checks for a progress indicator between each delete/download operation. So the update process may be interrupted and continued later.
 *
 * Returns true if any icons were updated.
 */
@Slow
internal fun updateIconsAtDir(
  existingMetadata: MaterialIconsMetadata,
  newMetadata: MaterialIconsMetadata,
  targetDir: Path,
  iconsUrlProvider: MaterialIconsUrlProvider): Boolean {
  cleanupUnusedIcons(existingMetadata, targetDir)

  // The metadata builder should reflect the current status of the metadata during the process, so deletions or additions of icons should be
  // updated here.
  val metadataBuilder =
    MaterialIconsMetadataBuilder(
      host = newMetadata.host,
      urlPattern = newMetadata.urlPattern,
      families = newMetadata.families
    )
  existingMetadata.icons.forEach(metadataBuilder::addIconMetadata)

  val updateData = getIconsUpdateData(existingMetadata, newMetadata, iconsUrlProvider)
  if (updateData.isEmpty()) {
    log.info("No icons metadata update needed")
    return false
  }

  var isCancelled = ProgressManager.getInstance().progressIndicator?.isCanceled ?: false
  fun Collection<MaterialMetadataIcon>.cancellableForEachIcon(onIcon: (iconMetadata: MaterialMetadataIcon) -> Unit) {
    this.forEach { iconMetadata: MaterialMetadataIcon ->
      if (isCancelled || ProgressManager.getInstance().progressIndicator?.isCanceled == true) {
        isCancelled = true
        return@forEach
      }
      onIcon(iconMetadata)
    }
  }

  updateData.iconsToRemove.cancellableForEachIcon(metadataBuilder::removeIconMetadata)
  updateData.iconsToDownload.cancellableForEachIcon { iconMetadata ->
    try {
      downloadIconStyles(newMetadata, targetDir, iconMetadata)

      // Icon should be registered until after the download finishes
      metadataBuilder.addIconMetadata(iconMetadata)
    }
    catch (e: Exception) {
      when (e) {
        // Don't include the ProcessCanceledException in the Log
        is ProcessCanceledException -> log.info("Download cancelled for: ${iconMetadata.name}", e)
        else -> log.warn("Download error for: ${iconMetadata.name}", e)
      }
    }
  }

  // Update metadata file
  MaterialIconsMetadata.writeAsJson(metadataBuilder.build(), targetDir.resolve(METADATA_FILE_NAME), log)
  log.info("Updated icons remove=${updateData.iconsToRemove.size} download=${updateData.iconsToDownload}")
  return true
}

/**
 * Look through the icon directories in [targetDir], and delete all of those that are not present in [existingMetadata].
 *
 * Note that this involves looking through the directories of each Icon for each style/family.
 */
private fun cleanupUnusedIcons(existingMetadata: MaterialIconsMetadata, targetDir: Path) {
  // Set of expected style directories.
  val styleDirNames = existingMetadata.families.map { it.toDirFormat() }.toSet()
  // Set of expected icon names within each style directory.
  val iconNamesSet = existingMetadata.icons.map { it.name }.toSet()
  // List of paths for icon directories that do not correspond to any icon in the existing metadata.
  val unusedIconDirPaths = arrayListOf<Path>()

  // Populate the list of unused icon directories, depth 2 to just walk style and icon directories.
  CancellableFileIo.walkFileTree(targetDir, emptySet(), 2, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (styleDirNames.contains(dir.name) || targetDir.isSameFileAs(dir)) {
        // Only visit expected directories.
        return FileVisitResult.CONTINUE
      }
      // TODO(b/227805896): Consider the possibility that a style may be removed in an update, in which case, we'd have to delete the
      //  directories that don't match to an expected style.
      return FileVisitResult.SKIP_SUBTREE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (attrs.isDirectory && !iconNamesSet.contains(file.name)) {
        // The only directories passed here can only be icon directories, so any that do not match are an unused icon directory.
        unusedIconDirPaths.add(file)
      }
      return FileVisitResult.CONTINUE
    }
  })

  // Proceed to delete unused icon directories, check for progress cancellation between deletions.
  unusedIconDirPaths.forEach { path ->
    ProgressManager.checkCanceled()
    try {
      path.delete(recursively = true)
    }
    catch (e: Exception) {
      log.warn("Error deleting unused icon directory: ${path.name}", e)
    }
  }
}

/**
 * Downloads all styles for the given [iconMetadata] to the [targetDir].
 *
 * Deletes existing files of [iconMetadata] before downloading.
 */
private fun downloadIconStyles(metadata: MaterialIconsMetadata, targetDir: Path, iconMetadata: MaterialMetadataIcon) {
  log.info("downloadIconStyles to $targetDir")
  val fileDescriptions = metadata.families
    .filter {
      !iconMetadata.unsupportedFamilies.contains(it)
    }
    .map { style ->
      targetDir.resolve(style.toDirFormat()).createDirectories()
      createMaterialIconFileDescription(metadata, iconMetadata, style)
    }
  val downloader = DownloadableFileService.getInstance().createDownloader(fileDescriptions, "Material Icons")
  val downloaded = downloader.download(targetDir.toFile()).map { it.first }
  log.info("downloadIconStyles downloaded ${downloaded.size} files")
  val renamedFiles = renameDownloadedFiles(downloaded)
  cleanUpDownloadDirectories(renamedFiles)
}

/**
 * Returns a [DownloadableFileDescription] using the url pattern from [MaterialIconsMetadata.urlPattern].
 */
private fun createMaterialIconFileDescription(
  metadata: MaterialIconsMetadata,
  iconMetadata: MaterialMetadataIcon,
  style: String): DownloadableFileDescription {
  val styleDirName = style.toDirFormat()
  val iconName = iconMetadata.name
  val host = metadata.host
  val basePattern = if (style.contains("Symbols"))
    "/s/i/short-term/release/{family}/{icon}/default/{asset}"
  else
    metadata.urlPattern
  val pattern = basePattern
    .replace("{family}", styleDirName)
    .replace("{icon}", iconName)
    .replace("{version}", iconMetadata.version.toString())
    .replace("{asset}", "24px.xml")
  val downloadUrl = "https://%1s%2s".format(host, pattern)
  val fileName =
    styleDirName +
    File.separatorChar +
    iconName +
    File.separatorChar +
    getIconFileNameWithoutExtension(iconName, styleDirName) +
    ".tmp"
  return DownloadableFileService.getInstance().createFileDescription(downloadUrl, fileName)
}

/**
 * Returns an object with the icons that should be removed & downloaded.
 *
 * Icons to delete are those in [oldMetadata] that are not present in [newMetadata].
 *
 * Icons to download are those in [newMetadata] that are not present in [oldMetadata] or that have a higher [MaterialMetadataIcon.version].
 * [iconsUrlProvider] will allow this method to look for broken icons that are part of the [oldMetadata] and [newMetadata] but that, for
 * some reason, the icon is not present.
 */
@Slow
private fun getIconsUpdateData(
  oldMetadata: MaterialIconsMetadata,
  newMetadata: MaterialIconsMetadata,
  iconsUrlProvider: MaterialIconsUrlProvider): IconsUpdateData {
  val commonFamilies = oldMetadata.families.intersect(newMetadata.families.asIterable())
  val commonIcons = oldMetadata.icons.intersect(newMetadata.icons.asIterable())
  val brokenIcons = commonFamilies.flatMap { family ->
    return@flatMap if (iconsUrlProvider.getStyleUrl(family) == null)
      emptySequence()
    else
      commonIcons.filter { icon ->
        if (icon.unsupportedFamilies.contains(family)) return@filter false // This is not broken since it's not supported by this family
        val expectedFileName = getIconFileNameWithoutExtension(iconName = icon.name, styleName = family) + SdkConstants.DOT_XML
        val iconPath = iconsUrlProvider.getIconUrl(family, icon.name, expectedFileName)?.path ?: return@filter false

        !Path.of(iconPath).exists()
      }.asSequence()
  }

  // Icons can have the same name but be from different styles. Typically, you will have two versions
  // of the same icon, one for Material Icons and one for Material Symbols.
  val existingIcons = oldMetadata.icons.toSet()
  val newIcons = newMetadata.icons.toSet()

  val iconsToRemove = existingIcons.subtract(newIcons)
  val iconsToDownload = newIcons.subtract(existingIcons) + brokenIcons

  if (iconsToRemove.isNotEmpty()) {
    log.info("${iconsToRemove.size} icons removed from metadata.")
  }
  if (iconsToDownload.isNotEmpty()) {
    log.info("${iconsToDownload.size} icons to download.")
  }

  return IconsUpdateData(iconsToRemove, iconsToDownload)
}

/**
 * Removes any remaining files in the directories of the downloaded icons, so that there's only the downloaded file in each of the icon
 * directories.
 *
 * E.g. if after the download .../my_icon/ contains 'old_icon.xml' and 'new_icon.xml', 'old_icon.xml' is removed.
 */
private fun cleanUpDownloadDirectories(downloadedFiles: List<File>) {
  downloadedFiles.forEach { downloadedFile ->
    val iconDirectory = downloadedFile.parentFile
    val filesToCleanUp = iconDirectory.listFiles()?.filter { it.name != downloadedFile.name } ?: emptyList()
    filesToCleanUp.forEach {
      if (!it.delete()) {
        // Only one file is expected in the icon directory, so not being able to delete old files could be an issue.
        throw IllegalStateException("Unable to delete file: ${it.name}")
      }
    }
  }
}

/**
 * Renames the downloaded files to the expected naming scheme for material icons.
 *
 * @return The list of files with their new name.
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
  val iconsToRemove: Set<MaterialMetadataIcon>,
  val iconsToDownload: Set<MaterialMetadataIcon>
) {
  fun isEmpty() = iconsToRemove.isEmpty() && iconsToDownload.isEmpty()
}