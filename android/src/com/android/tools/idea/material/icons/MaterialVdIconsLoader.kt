/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.MaterialIconsUtils.toDirFormat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.util.zip.ZipEntry

private val LOG = Logger.getInstance(MaterialVdIconsLoader::class.java)

/**
 * Loads Material [VdIcon]s from the [urlProvider] based on the given [MaterialIconsMetadata].
 */
class MaterialVdIconsLoader(
  private val metadata: MaterialIconsMetadata,
  private val urlProvider: MaterialIconsUrlProvider = MaterialIconsUrlProviderImpl()
) {

  /**
   * Based on the given [MaterialIconsMetadata], gets the [VdIcon]s bundled with Android Studio.
   *
   * TODO: Return/load & update icons by style, instead of loading them all in one call.
   */
  @Slow
  fun getMaterialVdIcons(): MaterialVdIcons {
    val styles = metadata.families
    val styleCategoryToSortedIcons: HashMap<String, Map<String, Array<VdIcon>>> = HashMap()
    val styleToSortedIcons = HashMap<String, Array<VdIcon>>()

    // Helper map to quickly verify the icon file exists in the metadata and get the icon's metadata.
    val iconNameToIconMetadata = HashMap<String, MaterialMetadataIcon>()
    metadata.icons.forEach { iconMetadata ->
      iconNameToIconMetadata[iconMetadata.name] = iconMetadata
    }

    val styleToIcons: MultiMap<String, VdIcon> = MultiMap()
    styles.forEach { style ->
      // Map where the categories are extracted from existing icon's metadata and mapped to their files.
      val categoriesToIcons: MultiMap<String, VdIcon> = MultiMap()

      // Visitor callback to traverse ZipEntrys and load the VectorDrawable files into VdIcon.
      val iconZipVisitor: (ZipEntry) -> Unit = { entry ->
        val entrySplitName = entry.name.split(File.separatorChar)
        // Expected format for a 'bar' icon under the 'foo' style is: "foo/bar/any.xml"
        val entryFileName = entrySplitName.getOrElse(entrySplitName.lastIndex) { "" }
        val entryFileParent = entrySplitName.getOrElse(entrySplitName.lastIndex - 1) { "" }
        val entryStyleName = entrySplitName.getOrElse(entrySplitName.lastIndex - 2) { "" }
        if (style.toDirFormat() == entryStyleName && iconNameToIconMetadata.contains(
            entryFileParent) && entryFileName.isNotEmpty() && entryFileName.endsWith(SdkConstants.DOT_XML, true)) {
          // Verify that the ZipEntry is for an existing icon in the metadata.
          val iconMetadata = iconNameToIconMetadata[entryFileParent]!!
          getIcon(style, iconMetadata.name, entryFileName)?.let { url ->
            // Load the icon file (XML) into VdIcon and map it with help of the metadata.
            val vdIcon = VdIcon(url).apply { setShowName(true) }
            styleToIcons.putValue(style, vdIcon)
            iconMetadata.categories.forEach { category ->
              categoriesToIcons.putValue(category, vdIcon)
            }
          }
        }
      }
      // Visitor callback to traverse Files and load the VectorDrawable files into VdIcon.
      val iconFileVisitor: (File) -> Unit = { iconFile ->
        val iconFolderName = iconFile.parentFile.name
        if (iconNameToIconMetadata.containsKey(iconFolderName)) {
          // Verify that the File is for an existing icon in the metadata.
          val iconMetadata = iconNameToIconMetadata[iconFolderName]!!
          getIcon(style, iconMetadata.name, iconFile.name)?.let { url ->
            // Load the icon file (XML) into VdIcon and map it with help of the metadata.
            val vdIcon = VdIcon(url).apply { setShowName(true) }
            styleToIcons.putValue(style, vdIcon)
            iconMetadata.categories.forEach { category ->
              categoriesToIcons.putValue(category, vdIcon)
            }
          }
        }
      }
      // Open the url and traverse the files through the visitor callbacks.
      openAndVisitIconFiles(urlProvider.getStyleUrl(style), iconZipVisitor, iconFileVisitor)

      val categoriesToSortedIcons = HashMap<String, Array<VdIcon>>()
      categoriesToIcons.keySet().forEach { category ->
        // Ensure the icon files are sorted by their display name.
        val sortedIcons = categoriesToIcons[category].sortedBy { it.displayName }.toTypedArray()
        categoriesToSortedIcons[category] = sortedIcons
      }
      styleCategoryToSortedIcons[style] = categoriesToSortedIcons
    }
    styleToIcons.keySet().forEach { style ->
      // Ensure the icon files are sorted by their display name.
      val sortedIcons = styleToIcons[style].sortedBy { it.displayName }
      styleToSortedIcons[style] = sortedIcons.toTypedArray()
    }
    return MaterialVdIcons(styleCategoryToSortedIcons, styleToSortedIcons)
  }

  private fun getIcon(style: String, iconName: String, iconFileName: String): URL? {
    return urlProvider.getIconUrl(style, iconName, iconFileName)
  }

  private fun openAndVisitIconFiles(url: URL?, iconZipVisitor: (ZipEntry) -> Unit, iconFileVisitor: (File) -> Unit) {
    // TODO: Make sure this cannot be called more than once simultaneously. May cause IOExceptions from 'openConnection'.
    if (url == null) return
    when (url.protocol) {
      "file" -> visitFiles(File(url.path), iconFileVisitor)
      "jar" -> {
        try {
          val connection = url.openConnection() as JarURLConnection
          connection.jarFile.stream().forEach(iconZipVisitor)
        }
        catch (e: IOException) {
          LOG.error("Error reading material icon files.", e)
          return
        }
      }
    }
  }

  private fun visitFiles(file: File, visitor: (File) -> Unit) {
    file.listFiles()?.forEach { iconFile ->
      iconFile.listFiles()?.let { childFiles ->
        if (childFiles.size == 1 && !childFiles.first().isDirectory) {
          visitor(childFiles.first())
        }
      }
    }
  }
}

/**
 * The model for the Material [VdIcon]s loaded.
 */
class MaterialVdIcons(
  private val styleCategoryToSortedIcons: Map<String, Map<String, Array<VdIcon>>>,
  private val styleToSortedIcons: Map<String, Array<VdIcon>>
) {

  val styles: Array<String> = styleCategoryToSortedIcons.keys.sorted().toTypedArray()

  fun getCategories(style: String): Array<String> {
    return styleCategoryToSortedIcons[style]?.keys?.sorted()?.toTypedArray() ?: arrayOf<String>()
  }

  fun getIcons(style: String, category: String): Array<VdIcon> {
    return styleCategoryToSortedIcons[style]?.get(category) ?: arrayOf<VdIcon>()
  }

  fun getAllIcons(style: String): Array<VdIcon> {
    return styleToSortedIcons[style] ?: arrayOf()
  }

  companion object {
    val EMPTY = MaterialVdIcons(emptyMap(), emptyMap())
  }
}