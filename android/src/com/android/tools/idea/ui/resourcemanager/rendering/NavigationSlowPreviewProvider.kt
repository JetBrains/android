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

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceUrl
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.resolve
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.plugin.LayoutRenderer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.android.facet.AndroidFacet
import java.awt.image.BufferedImage
import java.io.File

/**
 * [SlowResourcePreviewProvider] for Navigation assets.
 *
 * For valid Navigation graphs, returns a preview of the layout set as the start destination, if there's no defined layout, it will just
 * return a layout placeholder image.
 */
class NavigationSlowPreviewProvider(
  private val facet: AndroidFacet,
  private val resourceResolver: ResourceResolver
) : SlowResourcePreviewProvider {
  override val previewPlaceholder: BufferedImage = createNavigationPlaceHolder(JBUIScale.scale(100), JBUIScale.scale(100), null)

  override fun getSlowPreview(width: Int, height: Int, asset: Asset): BufferedImage? {
    val designAsset = (asset as? DesignAsset) ?: return null
    val layoutFileToPreview = resolveLayoutToRender(designAsset)
    val layoutPreview = layoutFileToPreview?.let {
      val configuration = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(layoutFileToPreview.virtualFile)
      LayoutRenderer.getInstance(facet).getLayoutRender(layoutFileToPreview, configuration)
    }?.get()
    // TODO(147157808): Provide a different visual when there is nothing to preview. E.g: 'No preview' text
    return createNavigationPlaceHolder(width, height, layoutPreview)
  }

  /**
   * For the given Navigation asset, it will try to find what's the layout placeholder for the start destination.
   *
   * This means that it'll look for [SdkConstants.ATTR_START_DESTINATION] in the root tag, and then look for the tag with the corresponding
   * ID, if that tag has a [SdkConstants.ATTR_LAYOUT], this will return the [XmlFile] associated with that layout resource.
   */
  private fun resolveLayoutToRender(assetToRender: DesignAsset): XmlFile? {
    val navPsiFile = runReadAction { PsiManager.getInstance(facet.module.project).findFile(assetToRender.file) as? XmlFile } ?: return null
    val rootTag = runReadAction { navPsiFile.rootTag } ?: return null
    val destinationId = rootTag.readAttributeOrNull(SdkConstants.ATTR_START_DESTINATION, SdkConstants.AUTO_URI) ?: return null
    val shortDestId = ResourceUrl.parse(destinationId)?.name ?: return null
    val destTag = rootTag.childrenOfType<XmlTag>().firstOrNull { tag ->
      // Find the Tag with the ID of the start destination attribute.
      val tagId = tag.readAttributeOrNull(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI) ?: return null
      val tagIdValue = ResourceUrl.parse(tagId)?.name
      return@firstOrNull tagIdValue?.equals(shortDestId) ?: false
    } ?: return null
    // Then see if it has the tools:layout attribute and get the file associated with that resource.
    val layoutUrl = destTag.readAttributeOrNull(SdkConstants.ATTR_LAYOUT, SdkConstants.TOOLS_URI) ?: return null
    val layoutResourceUrl = ResourceUrl.parse(layoutUrl) ?: return null
    val layoutResourceValue = runReadAction { resourceResolver.resolve(layoutResourceUrl, navPsiFile)?.value } ?: return null
    val layoutVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(layoutResourceValue)) ?: return null
    return runReadAction { PsiManager.getInstance(facet.module.project).findFile(layoutVirtualFile) as? XmlFile }
  }
}

private fun XmlTag.readAttributeOrNull(attribute: String, namespaceUri: String): String? = runReadAction {
  getAttributeValue(attribute, namespaceUri)
}