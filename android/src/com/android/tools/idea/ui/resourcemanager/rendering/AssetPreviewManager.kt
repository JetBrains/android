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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.ImageCache
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import org.jetbrains.android.facet.AndroidFacet

/**
 * An [AssetPreviewManager] is used to manage [AssetIconProvider] and returns the
 * correct one for a given [ResourceType].
 * @see AssetPreviewManagerImpl
 */
interface AssetPreviewManager {

  /**
   * Returns an [AssetIconProvider] capable of rendering [DesignAsset] of type [resourceType].
   */
  fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider

  /**
   * Returns an [AssetDataProvider] for basic [DesignAsset] data to be displayed.
   */
  fun getDataProvider(resourceType: ResourceType): AssetDataProvider
}

/**
 * Default implementation of [AssetPreviewManager]. The supported [ResourceType] are [ResourceType.DRAWABLE],
 * [ResourceType.LAYOUT], [ResourceType.COLOR] and [ResourceType.MIPMAP]
 */
class AssetPreviewManagerImpl(
  private val facet: AndroidFacet, imageCache: ImageCache, private val resourceResolver: ResourceResolver
) : AssetPreviewManager {

  private val colorPreviewProvider by lazy {
    ColorIconProvider(facet.module.project, resourceResolver)
  }
  private val drawablePreviewProvider by lazy {
    DrawableIconProvider(facet, resourceResolver, imageCache)
  }
  private val fontPreviewProvider by lazy {
    FontIconProvider(facet)
  }

  private val colorDataProvider by lazy {
    ColorAssetDataProvider(facet.module.project, resourceResolver)
  }
  private val valueDataProvider by lazy {
    ValueAssetDataProvider(resourceResolver)
  }
  private val defaultDataProvider by lazy {
    DefaultAssetDataProvider()
  }

  /**
   * Returns an [AssetIconProvider] for [ResourceType.COLOR], [ResourceType.DRAWABLE], [ResourceType.LAYOUT]
   */
  override fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider =
    when (resourceType) {
      ResourceType.COLOR -> colorPreviewProvider
      ResourceType.DRAWABLE,
      ResourceType.MIPMAP,
      ResourceType.MENU,
      ResourceType.LAYOUT -> drawablePreviewProvider
      ResourceType.FONT -> fontPreviewProvider
      else -> DefaultIconProvider.INSTANCE
    }

  /**
   * Returns an [AssetDataProvider] for some specific [ResourceType]s, their difference will mostly depend if it makes sense for the
   * resource to have an Icon preview, those that don't will have their resolved value in the [AssetData].
   */
  override fun getDataProvider(resourceType: ResourceType): AssetDataProvider =
    when (resourceType) {
      ResourceType.COLOR -> colorDataProvider
      ResourceType.ARRAY,
      ResourceType.BOOL,
      ResourceType.DIMEN,
      ResourceType.FRACTION,
      ResourceType.INTEGER,
      ResourceType.PLURALS,
      ResourceType.STRING -> valueDataProvider
      else -> defaultDataProvider
    }
}