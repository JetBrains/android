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
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import org.jetbrains.android.facet.AndroidFacet
import com.android.tools.idea.res.getItemPsiFile
import com.android.tools.idea.res.getItemTag
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

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

  private var selectedItems: List<Asset>? = null

  fun getData(dataId: String?, selectedAssets: List<Asset>): Any? {
    this.selectedItems = selectedAssets
    return when (dataId) {
      LangDataKeys.PSI_ELEMENT.name -> assetsToSingleElement()
      LangDataKeys.PSI_ELEMENT_ARRAY.name -> assetsToArrayPsiElements()
      PlatformDataKeys.COPY_PROVIDER.name -> this
      UsageView.USAGE_TARGETS_KEY.name -> getUsageTargets(assetsToArrayPsiElements())
      RESOURCE_DESIGN_ASSETS_KEY.name -> selectedAssets.mapNotNull { it as? DesignAsset }.toTypedArray()
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

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isCopyVisible(dataContext: DataContext): Boolean = isCopyEnabled(dataContext)

  override fun isCopyEnabled(dataContext: DataContext): Boolean = !selectedItems.isNullOrEmpty()

  private fun assetsToArrayPsiElements(): Array<out PsiElement> =
    selectedItems
      ?.mapNotNull(Asset::resourceItem)
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
      psiElement = getItemTag(facet.module.project, resourceItem)
        ?.getAttribute(SdkConstants.ATTR_NAME)?.valueElement
    }

    if (psiElement == null) {
      psiElement = getItemPsiFile(facet.module.project, resourceItem)
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

fun createTransferable(asset: Asset): Transferable {
  val resourceUrl = asset.resourceUrl

  return object : Transferable {
    override fun getTransferData(flavor: DataFlavor?): Any {
      return when (flavor) {
        RESOURCE_URL_FLAVOR -> resourceUrl
        DataFlavor.stringFlavor -> resourceUrl.toString()
        else -> UnsupportedFlavorException(flavor)
      }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor in SUPPORTED_DATA_FLAVORS

    override fun getTransferDataFlavors(): Array<DataFlavor> = SUPPORTED_DATA_FLAVORS

  }
}
