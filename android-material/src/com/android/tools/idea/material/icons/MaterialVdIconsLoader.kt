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
package com.android.tools.idea.material.icons

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.common.BundledIconsUrlProvider
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getIconFileNameWithoutExtension
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import java.io.IOException

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

  /**
   * Loads the icons bundled with AndroidStudio that correspond to the given [style], returns an updated [MaterialVdIcons].
   *
   * Every call to this method will update the backing model of all the icons loaded by this instance, so the returned [MaterialVdIcons]
   * will also include icons loaded in previous calls.
   */
  @Slow
  fun loadMaterialVdIcons(style: String): MaterialVdIcons {
    require(metadata.families.contains(style)) { "Style: $style not part of the metadata." }

    // A map that holds all icons for each style
    val styleToIcons: MultiMap<String, VdIcon> = MultiMap()
    // Map for icons per category, note that an icon may be available in more than one category
    val categoriesToIcons: MultiMap<String, VdIcon> = MultiMap()

    if (urlProvider.getStyleUrl(style) == null) {
      // Not used, but it's good to know if the provider fails here
      LOG.warn("No URL for style: $style. From provider: ${urlProvider::class.java.name}.")
    }

    metadata.icons.forEach iconsLoop@{ iconMetadata ->
      if (iconMetadata.unsupportedFamilies.contains(style)) {
        // Check that the icon is expected for the asked style
        return@iconsLoop
      }
      // The name should be consistent with bundled AND downloaded icons, to guarantee that we can find them
      val expectedFileName = getIconFileNameWithoutExtension(iconName = iconMetadata.name, styleName = style) + SdkConstants.DOT_XML
      val vdIcon = loadVdIcon(styleName = style, iconName = iconMetadata.name, fileName = expectedFileName)
      if (vdIcon != null) {
        styleToIcons.putValue(style, vdIcon)
        iconMetadata.categories.forEach { categoriesToIcons.putValue(it, vdIcon) }
      }
    }

    val categoriesToSortedIcons = HashMap<String, Array<VdIcon>>()
    categoriesToIcons.keySet().forEach { category ->
      // Make sure the icon files are sorted by their display name.
      val sortedIcons = categoriesToIcons[category].sortedBy { it.displayName }.toTypedArray()
      categoriesToSortedIcons[category] = sortedIcons
    }
    styleCategoryToSortedIcons[style] = categoriesToSortedIcons
    // Make sure icon files are sorted by their display name.
    val sortedIcons = styleToIcons[style].sortedBy { it.displayName }
    styleToSortedIcons[style] = sortedIcons.toTypedArray()
    return MaterialVdIcons(styleCategoryToSortedIcons, styleToSortedIcons)
  }

  private fun loadVdIcon(styleName: String, iconName: String, fileName: String): VdIcon? {
    val iconUrl = urlProvider.getIconUrl(styleName, iconName, fileName)
    if (iconUrl == null) {
      LOG.warn("Could not obtain Icon URL: Name=$iconName FileName=$fileName. From provider: ${urlProvider::class.java.name}.")
      return null
    }
    return try {
      VdIcon(iconUrl).apply { setShowName(true) }
    }
    catch (e: IOException) {
      LOG.warn("Failed to load material icon $iconUrl", e)
      return null
    }
  }
}