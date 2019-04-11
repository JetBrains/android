/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.viewmodel

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.ui.resourcemanager.getPNGResourceItem
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceDataManagerTest {

  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  @Before
  fun setUp() {
    rule.fixture.testDataPath = getTestDataDirectory()
  }

  @Test
  fun getColorPsiElement() {
    rule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val colorItem = ResourceRepositoryManager.getInstance(rule.module.androidFacet!!)
      .appResources
      .getResources(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary")
      .first()
    val colorAsset = DesignAsset.fromResourceItem(colorItem)!!

    val dataManager = ResourceDataManager(rule.module.androidFacet!!)
    val psiArray = runInEdtAndGet { dataManager.getData(LangDataKeys.PSI_ELEMENT_ARRAY.name, listOf(colorAsset)) as Array<PsiElement> }
    assertEquals("colorPrimary", runInEdtAndGet { (psiArray[0] as XmlAttributeValue).value })

    val copyProvider = dataManager.getData(PlatformDataKeys.COPY_PROVIDER.name, listOf(colorAsset)) as CopyProvider
    assertTrue { copyProvider.isCopyEnabled(DataContext.EMPTY_CONTEXT) }

    val usageTargetKey = runInEdtAndGet {
      dataManager.getData(UsageView.USAGE_TARGETS_KEY.name, listOf(colorAsset)) as Array<UsageTarget?>
    }
    assertTrue { usageTargetKey.isNotEmpty() }

    dataManager.performCopy(DataContext.EMPTY_CONTEXT)
    assertEquals("@color/colorPrimary", CopyPasteManager.getInstance().getContents<ResourceUrl>(RESOURCE_URL_FLAVOR)!!.toString())
  }

  @Test
  fun getFilePsiElement() {
    val pngItem = rule.getPNGResourceItem()
    val colorAsset = DesignAsset.fromResourceItem(pngItem)!!

    val dataManager = ResourceDataManager(rule.module.androidFacet!!)
    val psiArray = runInEdtAndGet { dataManager.getData(LangDataKeys.PSI_ELEMENT_ARRAY.name, listOf(colorAsset)) as Array<PsiElement> }
    assertEquals(pngItem.getSourceAsVirtualFile(), runInEdtAndGet { PsiUtil.getVirtualFile(psiArray[0].containingFile)!! })

    val copyProvider = dataManager.getData(PlatformDataKeys.COPY_PROVIDER.name, listOf(colorAsset)) as CopyProvider
    assertTrue { copyProvider.isCopyEnabled(DataContext.EMPTY_CONTEXT) }

    val usageTargetKey = runInEdtAndGet {
      dataManager.getData(UsageView.USAGE_TARGETS_KEY.name, listOf(colorAsset)) as Array<UsageTarget?>
    }
    assertTrue { usageTargetKey.isNotEmpty() }
  }
}