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
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget

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
class AssetPreviewManagerImpl(val facet: AndroidFacet, currentFile: VirtualFile?, imageCache: ImageCache) : AssetPreviewManager {

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

  // TODO: Optionally, receive a resource resolver.
  private var resourceResolver = createResourceResolver(facet, currentFile = currentFile)

  /**
   * Returns an [AssetIconProvider] for [ResourceType.COLOR], [ResourceType.DRAWABLE], [ResourceType.LAYOUT]
   */
  override fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider =
    when (resourceType) {
      ResourceType.COLOR -> colorPreviewProvider
      ResourceType.DRAWABLE,
      ResourceType.MIPMAP,
      ResourceType.LAYOUT -> drawablePreviewProvider
      ResourceType.FONT -> fontPreviewProvider
      else -> DefaultIconProvider.INSTANCE
    }

  /**
   * Returns an [AssetDataProvider] for some specific [ResourceType]s, their difference will mostly depend if it makes sense for the
   * resource to have an Icon preview, those that don't will have their resolved value in the [AssetData].
   */
  override fun getDataProvider(resourceType: ResourceType): AssetDataProvider =
    when(resourceType) {
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

private fun createResourceResolver(androidFacet: AndroidFacet, currentFile: VirtualFile?): ResourceResolver {
  val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet)
  currentFile?.let {
    return configurationManager.getConfiguration(currentFile).resourceResolver
  }
  val manifest = MergedManifestManager.getSnapshot(androidFacet)
  val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
  val target = configurationManager.highestApiTarget?.let { StudioEmbeddedRenderTarget.getCompatibilityTarget(it) }
  return configurationManager.resolverCache.getResourceResolver(target, theme, FolderConfiguration.createDefault())
}