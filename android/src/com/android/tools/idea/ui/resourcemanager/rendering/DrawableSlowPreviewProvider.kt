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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.res.resolveDrawable
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.android.tools.idea.ui.resourcemanager.plugin.FrameworkDrawableRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

/**
 * [SlowResourcePreviewProvider] for Drawable and Mipmap resources.
 */
class DrawableSlowPreviewProvider(
  private val facet: AndroidFacet,
  private val resourceResolver: ResourceResolver,
  private val contextFile: VirtualFile?) : SlowResourcePreviewProvider {
  private val project = facet.module.project

  override val previewPlaceholder: BufferedImage = createDrawablePlaceholderImage(JBUIScale.scale(20), JBUIScale.scale(20))

  override fun getSlowPreview(width: Int, height: Int, asset: Asset): BufferedImage? {
    val designAsset = asset as? DesignAsset ?: return null
    val configContext = contextFile ?: designAsset.file
    val dimension = Dimension(width, height)
    if (designAsset.resourceItem is SampleDataResourceItem) {
      // Do not try to resolve SampleData, it will return an iterable resourceValue that will generate different previews.
      val file = designAsset.file
      return DesignAssetRendererManager.getInstance().getViewer(file).getImage(file, facet.module, dimension, configContext).get()
    }
    val resolveValue = resourceResolver.resolveValue(designAsset) ?: return null
    if (resolveValue.isFramework) {
      // Delegate framework resources to FrameworkDrawableRenderer. DesignAssetRendererManager fails to provide an image for framework xml
      // resources, it tries to just use the file reference in ResourceValue but it needs a whole ResourceValue from the ResourceResolver
      // that also points to LayoutLib's framework resources instead of the local Android Sdk.
      return renderFrameworkDrawable(resolveValue, configContext, designAsset, dimension)?.get() ?: return null
    }

    val file = resourceResolver.resolveDrawable(resolveValue, project) ?: designAsset.file
    return DesignAssetRendererManager.getInstance().getViewer(file).getImage(file, facet.module, dimension, configContext).get()
  }

  private fun renderFrameworkDrawable(resolvedValue: ResourceValue,
                                      configContext: VirtualFile,
                                      designAsset: DesignAsset,
                                      dimension: Dimension): CompletableFuture<out BufferedImage?>? {
    val frameworkValue =
      if (designAsset.resourceItem.type == ResourceType.ATTR) {
        // For theme attributes, we can just use the already resolved value.
        resolvedValue
      }
      else {
        // Need a LayoutLib resolved value, so we resolve the resource's reference instead of its value.
        resourceResolver.getUnresolvedResource(designAsset.resourceItem.referenceToSelf) ?: return null
      }
    return FrameworkDrawableRenderer.getInstance(facet).getDrawableRender(frameworkValue, configContext, dimension)
  }
}