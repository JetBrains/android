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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceItem
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.LocalResourceRepository
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable


/**
 * [DataFlavor] for [ResourceUrl]
 */
@JvmField
val RESOURCE_URL_FLAVOR = DataFlavor(ResourceUrl::class.java, "Resource Url")

private val SUPPORTED_DATA_FLAVORS = arrayOf(RESOURCE_URL_FLAVOR, DataFlavor.stringFlavor)

/**
 * Helper class to deal with [DataContext] and copy/paste behavior from the resource explorer.
 */
class ResourceDataManager(var facet: AndroidFacet) : CopyProvider {

  private var selectedItems: List<DesignAsset>? = null

  fun getData(dataId: String?, selectedAssets: List<DesignAsset>): Any? {
    this.selectedItems = selectedAssets
    return when (dataId) {
      LangDataKeys.PSI_ELEMENT.name -> assetsToSingleElement()
      LangDataKeys.PSI_ELEMENT_ARRAY.name -> assetsToArrayPsiElements()
      PlatformDataKeys.COPY_PROVIDER.name -> this
      UsageView.USAGE_TARGETS_KEY.name -> getUsageTargets(assetsToArrayPsiElements())
      else -> null
    }
  }

  override fun performCopy(dataContext: DataContext) {
    selectedItems?.let {
      if (it.isNotEmpty()) {
        val designAsset = it.first()
        CopyPasteManager.getInstance().setContents(createTransferable(designAsset))
      }
    }
  }

  override fun isCopyVisible(dataContext: DataContext): Boolean = isCopyEnabled(dataContext)

  override fun isCopyEnabled(dataContext: DataContext): Boolean = !selectedItems.isNullOrEmpty()

  private fun assetsToArrayPsiElements(): Array<out PsiElement> =
    selectedItems
      ?.mapNotNull(DesignAsset::resourceItem)
      ?.mapNotNull(this::findPsiElement)
      ?.filter { it.manager.isInProject(it) }
      ?.toTypedArray() ?: emptyArray()

  /**
   * Try to find the psi element that this [ResourceItem] represents.
   */
  fun findPsiElement(resourceItem: ResourceItem): PsiElement? {
    var psiElement: PsiElement? = null
    if (!resourceItem.isFileBased
        && ResourceFolderType.VALUES in FolderTypeRelationship.getRelatedFolders(resourceItem.type)) {
      psiElement = LocalResourceRepository
        .getItemTag(facet.module.project, resourceItem)
        ?.getAttribute(SdkConstants.ATTR_NAME)?.valueElement
    }

    if (psiElement == null) {
      psiElement = AndroidResourceUtil.getItemPsiFile(facet.module.project, resourceItem)
    }
    return psiElement
  }

  private fun assetsToSingleElement(): PsiElement? {
    if (selectedItems?.size != 1) return null
    return assetsToArrayPsiElements().firstOrNull()
  }

  private fun getUsageTargets(chosenElements: Array<out PsiElement>?): Array<UsageTarget?> {
    if (chosenElements != null) {
      val usageTargets = arrayOfNulls<UsageTarget>(chosenElements.size)
      for (i in chosenElements.indices) {
        usageTargets[i] = PsiElement2UsageTargetAdapter(chosenElements[i])
      }
      return usageTargets
    }
    return emptyArray()
  }
}

fun createTransferable(assetSet: DesignAsset): Transferable {
  return object : Transferable {
    override fun getTransferData(flavor: DataFlavor?): Any? = when (flavor) {
      RESOURCE_URL_FLAVOR -> getResourceUrl(assetSet)
      DataFlavor.stringFlavor -> getResourceUrl(assetSet).toString()
      else -> null
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor in SUPPORTED_DATA_FLAVORS

    override fun getTransferDataFlavors(): Array<DataFlavor> = SUPPORTED_DATA_FLAVORS

  }
}

private fun getResourceUrl(asset: DesignAsset) =
  asset.resourceItem.referenceToSelf.resourceUrl