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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceMergerItem
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getPNGResourceItem
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.usages.UsageView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
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
  fun getResourceUrlForThemeAttributesAndSampleData() {
    val dataManager = ResourceDataManager(rule.module.androidFacet!!)

    val attrResource = ResourceMergerItem("my_attr", ResourceNamespace.RES_AUTO, ResourceType.ATTR, null, null, null)
    ResourceFile.createSingle(File("source"), attrResource, "")
    val colorAttributeAsset = Asset.fromResourceItem(attrResource, ResourceType.COLOR)

    runReadAction {
      PlatformDataKeys.COPY_PROVIDER.getData(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        dataManager.uiDataSnapshot(sink, listOf(colorAttributeAsset))
      })?.performCopy(DataContext.EMPTY_CONTEXT)

      val resourceUrl = CopyPasteManager.getInstance().getContents<ResourceUrl>(RESOURCE_URL_FLAVOR)
      Truth.assertThat(resourceUrl).isNotNull()
      Truth.assertThat(resourceUrl!!.toString()).isEqualTo("?attr/my_attr")

      val toolsResource = ResourceMergerItem("my_sample", ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA, null, null, null)
      ResourceFile.createSingle(File("sample"), toolsResource, "")
      val drawableSample = Asset.fromResourceItem(toolsResource, ResourceType.DRAWABLE)

      PlatformDataKeys.COPY_PROVIDER.getData(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        dataManager.uiDataSnapshot(sink, listOf(drawableSample))
      })?.performCopy(DataContext.EMPTY_CONTEXT)

      val sampleResourceUrl = CopyPasteManager.getInstance().getContents<ResourceUrl>(RESOURCE_URL_FLAVOR)
      Truth.assertThat(sampleResourceUrl).isNotNull()
      Truth.assertThat(sampleResourceUrl!!.toString()).isEqualTo("@tools:sample/my_sample")
    }
  }

  @Test
  fun getColorPsiElement() {
    rule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val colorItem = StudioResourceRepositoryManager.getInstance(rule.module.androidFacet!!)
      .appResources
      .getResources(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary")
      .first()
    val colorAsset = Asset.fromResourceItem(colorItem)

    val dataManager = ResourceDataManager(rule.module.androidFacet!!)
    val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      dataManager.uiDataSnapshot(sink, listOf(colorAsset))
    }
    runReadAction {
      val psiArray = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context)!!
      assertEquals("colorPrimary", (psiArray[0] as XmlAttributeValue).value)

      val copyProvider = PlatformDataKeys.COPY_PROVIDER.getData(context)!!
      assertTrue { copyProvider.isCopyEnabled(DataContext.EMPTY_CONTEXT) }

      val usageTargetKey = UsageView.USAGE_TARGETS_KEY.getData(context)!!
      assertTrue { usageTargetKey.isNotEmpty() }

      copyProvider.performCopy(DataContext.EMPTY_CONTEXT)
      assertEquals("@color/colorPrimary", CopyPasteManager.getInstance().getContents<ResourceUrl>(RESOURCE_URL_FLAVOR)!!.toString())
    }
  }

  @Test
  fun getFilePsiElement() {
    val pngItem = rule.getPNGResourceItem()
    val colorAsset = Asset.fromResourceItem(pngItem)

    val dataManager = ResourceDataManager(rule.module.androidFacet!!)
    val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      dataManager.uiDataSnapshot(sink, listOf(colorAsset))
    }
    runReadAction {
      val psiArray = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context)!!
      assertEquals(pngItem.getSourceAsVirtualFile(), PsiUtil.getVirtualFile(psiArray[0].containingFile)!!)

      val copyProvider = PlatformDataKeys.COPY_PROVIDER.getData(context)!!
      assertTrue { copyProvider.isCopyEnabled(DataContext.EMPTY_CONTEXT) }

      val usageTargetKey = UsageView.USAGE_TARGETS_KEY.getData(context)!!
      assertTrue { usageTargetKey.isNotEmpty() }
    }
  }
}