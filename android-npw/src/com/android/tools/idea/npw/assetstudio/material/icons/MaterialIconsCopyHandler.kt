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
package com.android.tools.idea.npw.assetstudio.material.icons

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialIconsMetadataBuilder
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.android.utils.SdkUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val METADATA_TEMP_FILE_NAME = "icons_metadata_temp_copy.txt"

private val LOG = Logger.getInstance(MaterialIconsCopyHandler::class.java)

/**
 * Takes care of copying material icons files to another path.
 *
 * Copies icon by icon, meaning that it will first copy all styles of a single icon before moving to the next one.
 *
 * Creates a temporary File([METADATA_TEMP_FILE_NAME]) in the same directory while it's copying the icons, it will record every icon that
 * has been fully copied so far. This allows us to pick-up the copying process in case it was interrupted.
 *
 * When finished, it renames the file to [METADATA_FILE_NAME], so if there's already an existing File([METADATA_FILE_NAME]) in the target
 * copy path, it will not copy anything, since the copy has already been performed to that path. This is a one-time process.
 *
 * @param metadata The metadata for the existing icons
 * @param materialVdIcons Model of existing material icons files.
 */
class MaterialIconsCopyHandler(
  metadata: MaterialIconsMetadata,
  materialVdIcons: MaterialVdIcons
) {
  private val host = metadata.host
  private val urlPattern = metadata.urlPattern
  private val families = metadata.families

  private val iconNameToWriteData = HashMap<String, VdIconWriteData>()

  init {
    // Create data model where we have a relationship of icon names and all information required for it: URL by style, file name, metadata
    val iconNameToMetadata = HashMap<String, MaterialMetadataIcon>()
    metadata.icons.forEach {
      // Build a map of icons names and their metadata.
      iconNameToMetadata[it.name] = it
    }

    val iconNameToStyleAndUrl = HashMap<String, HashMap<String, VdIconURLWithFileName>>()
    // Build a map of icons names and the URL for every available style.
    materialVdIcons.styles.forEach { style ->
      materialVdIcons.getAllIcons(style).forEach { vdIcon ->
        // TODO: This is a bit hacky and error prone, the actual icon name is the directory name where the url file is stored.
        val iconName = vdIcon.displayName.replace(" ", "_")
        if (!iconNameToStyleAndUrl.contains(iconName)) {
          iconNameToStyleAndUrl[iconName] = HashMap<String, VdIconURLWithFileName>().apply {
            put(style, VdIconURLWithFileName(vdIcon.url, vdIcon.name))
          }
        }
        else {
          iconNameToStyleAndUrl[iconName]!![style] = VdIconURLWithFileName(vdIcon.url, vdIcon.name)
        }
      }
    }
    iconNameToMetadata.forEach { (iconName, metadata) ->
      if (iconNameToStyleAndUrl.containsKey(iconName)) {
        // Combine the information from both maps.
        // Note that this is error prone if the mapped icon name doesn't match for both maps.
        iconNameToWriteData[iconName] = VdIconWriteData(iconNameToStyleAndUrl[iconName]!!, metadata)
      }
      else {
        LOG.warn("Files not found for '$iconName'")
      }
    }
  }

  /**
   * Copy the given material icons and metadata files in to [targetPath].
   */
  @Slow
  fun copyTo(targetPath: File) {
    require(targetPath.isDirectory)
    if (alreadyCopied(targetPath)) {
      LOG.info("Icons have already been copied to this directory.")
      return
    }

    val metadataBuilder = restoreMetadata(targetPath)
    val iconsToCopy = getRemainingIconsToCopy(metadataBuilder)

    if (iconsToCopy.isEmpty()) {
      return
    }

    copyIcons(iconsToCopy, metadataBuilder, targetPath)
  }

  private fun getRemainingIconsToCopy(metadataBuilder: MaterialIconsMetadataBuilder): HashMap<String, VdIconWriteData> {
    val iconsToCopy = HashMap(iconNameToWriteData) // Don't modify the original data map.

    metadataBuilder.build().icons.forEach {
      // Remove any icons from the map that has already been copied.
      iconsToCopy.remove(it.name)
    }
    return iconsToCopy
  }

  private fun copyIcons(iconsToCopy: HashMap<String, VdIconWriteData>, metadataBuilder: MaterialIconsMetadataBuilder, targetPath: File) {
    var cancelled = false
    iconsToCopy.values.forEach { writeData ->
      if (ProgressManager.getInstance().progressIndicator?.isCanceled == true) {
        cancelled = true
        return@forEach
      }
      val iconMetadata = writeData.metadataIcon
      writeData.stylesToURLAndName.forEach { (family, urlAndFileName) ->
        if (!iconMetadata.unsupportedFamilies.contains(family)) {
          copyIcon(urlAndFileName.url, urlAndFileName.fileName, family, iconMetadata, targetPath)
        }
      }
      metadataBuilder.addIconMetadata(iconMetadata)
    }
    updateTemporaryMetadataFile(metadataBuilder, targetPath)
    if (!cancelled) {
      updateFinishedMetadataFileName(targetPath)
    }
  }

  private fun copyIcon(iconUrl: URL, iconFileName: String, family: String, iconMetadata: MaterialMetadataIcon, targetPath: File) {
    val vdIconDir = targetPath.resolve(family.toDirFormat()).resolve(iconMetadata.name).apply { mkdirs() }
    File(vdIconDir, iconFileName).writeText(iconUrl.readText())
  }

  private fun updateTemporaryMetadataFile(metadataBuilder: MaterialIconsMetadataBuilder, targetPath: File) {
    val tempFilePath = targetPath.toPath().resolve(METADATA_TEMP_FILE_NAME)
    MaterialIconsMetadata.writeAsJson(metadataBuilder.build(), tempFilePath, LOG)
  }

  private fun alreadyCopied(targetPath: File): Boolean {
    return with(File(targetPath, METADATA_FILE_NAME)) { !isDirectory && exists() }
  }

  private fun restoreMetadata(targetPath: File): MaterialIconsMetadataBuilder {
    val metadataBuilder = MaterialIconsMetadataBuilder(host = host, urlPattern = urlPattern, families = families)
    val metadataTempFile = File(targetPath, METADATA_TEMP_FILE_NAME)
    if (metadataTempFile.exists() && !metadataTempFile.isDirectory) {
      LOG.info("Continuing icons copy")
      val metadata = MaterialIconsMetadata.parse(SdkUtils.fileToUrl(metadataTempFile), LOG)
      metadata.icons.forEach {
        metadataBuilder.addIconMetadata(it)
      }
    }
    return metadataBuilder
  }

  private fun updateFinishedMetadataFileName(targetPath: File) {
    val metadataFinishedFile = targetPath.resolve(METADATA_FILE_NAME)
    val metadataTempFile = targetPath.resolve(METADATA_TEMP_FILE_NAME)

    if (!metadataTempFile.exists()) {
      LOG.warn("No temporary metadata file")
      return
    }

    try {
      Files.move(metadataTempFile.toPath(), metadataFinishedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } catch (e: IOException) {
      LOG.warn("Failed to rename temporary metadata file", e)
    }
  }
}

private data class VdIconWriteData(val stylesToURLAndName: HashMap<String, VdIconURLWithFileName>, val metadataIcon: MaterialMetadataIcon)

private data class VdIconURLWithFileName(val url: URL, val fileName: String)