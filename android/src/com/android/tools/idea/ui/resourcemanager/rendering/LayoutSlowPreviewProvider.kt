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

import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.toFileResourcePathString
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.android.tools.idea.ui.resourcemanager.plugin.LayoutRenderer
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.android.facet.AndroidFacet
import java.awt.image.BufferedImage

/**
 * [SlowResourcePreviewProvider] for Layout and Menu resources.
 */
class LayoutSlowPreviewProvider(private val facet: AndroidFacet,
                                private val resourceResolver: ResourceResolver) : SlowResourcePreviewProvider {

  override val previewPlaceholder: BufferedImage = createLayoutPlaceholderImage(JBUIScale.scale(100), JBUIScale.scale(100))

  override fun getSlowPreview(width: Int, height: Int, asset: Asset): BufferedImage? {
    val designAsset = asset as? DesignAsset ?: return null
    val file = resourceResolver.getResolvedLayoutFile(designAsset) ?: return null
    val psiFile = AndroidPsiUtils.getPsiFileSafely(facet.module.project, file) as? XmlFile ?: return null
    val configuration = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(file)
    return LayoutRenderer.getInstance(facet).getLayoutRender(psiFile, configuration).get()
  }

  private fun ResourceResolver.getResolvedLayoutFile(designAsset: DesignAsset): VirtualFile? =
    if (designAsset.resourceItem.type == ResourceType.ATTR) {
      // For theme attributes, resolve the layout file.
      resolveValue(designAsset)?.value?.let {
        toFileResourcePathString(it)?.toVirtualFile()
      }
    }
    else {
      designAsset.file
    }
}