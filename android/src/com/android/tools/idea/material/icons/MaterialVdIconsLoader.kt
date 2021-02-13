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
import com.android.tools.idea.material.icons.common.BundledIconsUrlProvider
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.android.utils.SdkUtils
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
  private val urlProvider: MaterialIconsUrlProvider = BundledIconsUrlProvider()
) {

  private val styleCategoryToSortedIcons: HashMap<String, Map<String, Array<VdIcon>>> = HashMap()
  private val styleToSortedIcons = HashMap<String, Array<VdIcon>>()

  // Helper map to quickly verify the icon file exists in the metadata and get the icon's metadata.
  private val iconNameToIconMetadata = HashMap<String, MaterialMetadataIcon>().apply {
    metadata.icons.forEach { iconMetadata ->
      this[iconMetadata.name] = iconMetadata
    }
  }

  /**
   * Loads the icons bundled with AndroidStudio that correspond to the given [style], returns an updated [MaterialVdIcons].
   *
   * Every call to this method will update the backing model of all the icons loaded by this instance, so the returned [MaterialVdIcons]
   * will also include icons loaded in previous calls.
   */
  @Slow
  fun loadMaterialVdIcons(style: String): MaterialVdIcons {
    require(metadata.families.contains(style)) { "Style: $style not part of the metadata." }

    val styleToIcons: MultiMap<String, VdIcon> = MultiMap()
    // Map where the categories are extracted from existing icon's metadata and mapped to their files.
    val categoriesToIcons: MultiMap<String, VdIcon> = MultiMap()

    // Visitor callback to traverse ZipEntrys and load the VectorDrawable files into VdIcon.
    val iconZipVisitor: (ZipEntry) -> Unit = { entry ->
      val entrySplitName = entry.name.split('/')
      // Expected format for a 'bar' icon under the 'foo' style is: "foo/bar/any.xml"
      val entryFileName = entrySplitName.getOrElse(entrySplitName.lastIndex) { "" }
      val entryFileParent = entrySplitName.getOrElse(entrySplitName.lastIndex - 1) { "" }
      val entryStyleName = entrySplitName.getOrElse(entrySplitName.lastIndex - 2) { "" }
      if (style.toDirFormat() == entryStyleName && iconNameToIconMetadata.contains(entryFileParent) && entryFileName.isNotEmpty()
        && entryFileName.endsWith(SdkConstants.DOT_XML, true)) {
        // Verify that the ZipEntry is for an existing icon in the metadata.
        val iconMetadata = iconNameToIconMetadata[entryFileParent]!!
        loadIcon(style, iconMetadata.name, entryFileName)?.let { url ->
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
        loadIcon(style, iconMetadata.name, iconFile.name)?.let { url ->
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
    // Ensure the icon files are sorted by their display name.
    val sortedIcons = styleToIcons[style].sortedBy { it.displayName }
    styleToSortedIcons[style] = sortedIcons.toTypedArray()
    return MaterialVdIcons(styleCategoryToSortedIcons, styleToSortedIcons)
  }

  private fun loadIcon(style: String, iconName: String, iconFileName: String): URL? {
    val iconUrl = urlProvider.getIconUrl(style, iconName, iconFileName)
    if (iconUrl == null) {
      LOG.warn("Could not load icon: Name=$iconName FileName=$iconFileName")
    }
    return iconUrl
  }

  private fun openAndVisitIconFiles(url: URL?, iconZipVisitor: (ZipEntry) -> Unit, iconFileVisitor: (File) -> Unit) {
    // TODO: Make sure this cannot be called more than once simultaneously. May cause IOExceptions from 'openConnection'.
    if (url == null) return
    when (url.protocol) {
      "file" -> visitFiles(SdkUtils.urlToFile(url), iconFileVisitor)
      "jar" -> {
        try {
          val connection = url.openConnection() as JarURLConnection
          connection.jarFile.stream().forEach(iconZipVisitor)
        } catch (e: IOException) {
          LOG.error("Error reading material icon files.", e)
          return
        }
      }
      else -> LOG.error("Unsupported protocol: ${url.protocol}, will not load any icons")
    }
  }

  private fun visitFiles(file: File, visitor: (File) -> Unit) {
    val vectorDrawableFiles =
      file.listFiles()?.mapNotNull { iconDir ->
        iconDir.listFiles { fileInIconDir ->
          !fileInIconDir.isDirectory && fileInIconDir.name.endsWith(SdkConstants.DOT_XML, true)
        }?.firstOrNull() // For every icon directory, one 'xml' file is expected
      } ?: emptyList()
    vectorDrawableFiles.forEach(visitor)
  }
}