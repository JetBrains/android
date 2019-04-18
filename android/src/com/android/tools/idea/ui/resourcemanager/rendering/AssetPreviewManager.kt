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
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.ui.resourcemanager.ImageCache
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget

/**
 * An [AssetPreviewManager] is used to manage [AssetIconProvider] and return the
 * correct one for a given [ResourceType].
 * @see AssetPreviewManagerImpl
 */
interface AssetPreviewManager {

  /**
   * Returns an [AssetIconProvider] capable of rendering [DesignAsset] of type [resourceType].
   */
  fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider
}

/**
 * Default implementation of [AssetPreviewManager]. The supported [ResourceType] are [ResourceType.DRAWABLE],
 * [ResourceType.LAYOUT], [ResourceType.COLOR] and [ResourceType.MIPMAP]
 */
class AssetPreviewManagerImpl(val facet: AndroidFacet, imageCache: ImageCache) : AssetPreviewManager {

  private val colorPreviewProvider by lazy {
    ColorIconProvider(facet.module.project, resourceResolver)
  }
  private val drawablePreviewProvider by lazy {
    DrawableIconProvider(facet, resourceResolver, imageCache)
  }

  private var resourceResolver = createResourceResolver(facet)

  /**
   * Returns an [AssetIconProvider] for [ResourceType.COLOR], [ResourceType.DRAWABLE], [ResourceType.LAYOUT]
   */
  override fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider =
    when (resourceType) {
      ResourceType.COLOR -> colorPreviewProvider
      ResourceType.DRAWABLE,
      ResourceType.MIPMAP,
      ResourceType.LAYOUT -> drawablePreviewProvider
      else -> DefaultIconProvider.INSTANCE
    }
}

private fun createResourceResolver(androidFacet: AndroidFacet): ResourceResolver {
  val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet)
  val manifest = MergedManifestManager.getSnapshot(androidFacet)
  val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
  val target = configurationManager.highestApiTarget?.let { StudioEmbeddedRenderTarget.getCompatibilityTarget(it) }
  return configurationManager.resolverCache.getResourceResolver(target, theme, FolderConfiguration.createDefault())
}