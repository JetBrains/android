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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File

class AssetDataProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @Before
  fun setup() {
    runInAllowSaveMode {
      runInEdtAndWait {
        projectRule.fixture.testDataPath = getTestDataDirectory()
        projectRule.fixture.copyDirectoryToProject("res/", "res/")
      }
      projectRule.project.save()
    }
  }

  @Test
  fun testDefaultDataProvider() {
    val resName = "image"
    val fileName = "image.png"

    val defaultDataProvider = DefaultAssetDataProvider()
    val fakeDesignAsset = createFakeDesignAsset(fileName, ResourceType.DRAWABLE)

    val assetData = defaultDataProvider.getAssetData(fakeDesignAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo("0 B")

    val fakeAssetSet = ResourceAssetSet(resName, listOf(fakeDesignAsset))
    val assetSetData = defaultDataProvider.getAssetSetData(fakeAssetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(ResourceType.DRAWABLE.displayName)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }

  @Test
  fun testColorDataProvider() {
    val resName = "primary_color"
    val fileName = "colors.xml"
    val resValue = "#012345"

    val project = Mockito.mock(Project::class.java)
    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    val fakeDesignAsset = createFakeDesignAsset(fileName, ResourceType.COLOR)

    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(
      ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.COLOR, resName, resValue))

    val colorDataProvider = ColorAssetDataProvider(project, resourceResolver)

    val assetData = colorDataProvider.getAssetData(fakeDesignAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo("0 B")

    val fakeAssetSet = ResourceAssetSet(resName, listOf(fakeDesignAsset))
    val assetSetData = colorDataProvider.getAssetSetData(fakeAssetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(resValue)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }

  @Test
  fun testValueDataProvider() {
    val resName = "title"
    val fileName = "values.xml"
    val resValue = "Hello, World!"

    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    val fakeDesignAsset = createFakeDesignAsset(fileName, ResourceType.STRING)

    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(
      ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, resName, resValue))

    val valueDataProvider = ValueAssetDataProvider(resourceResolver)

    val assetData = valueDataProvider.getAssetData(fakeDesignAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo(resValue)

    val fakeAssetSet = ResourceAssetSet(resName, listOf(fakeDesignAsset))
    val assetSetData = valueDataProvider.getAssetSetData(fakeAssetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(resValue)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }

  @Test
  fun testPluralsDataProvider() {
    val resName = "coins_amount"
    val fileName = "plurals.xml"
    val resValue = "one: %s coin, other: %s coins"
    val resTruncatedValue = "one: %s coin, oth..."

    val pluralResource = StudioResourceRepositoryManager.getModuleResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.RES_AUTO, ResourceType.PLURALS).values().first()
    val designAsset = Asset.fromResourceItem(pluralResource) as DesignAsset

    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(pluralResource.resourceValue)

    val valueDataProvider = ValueAssetDataProvider(resourceResolver)

    val assetData = valueDataProvider.getAssetData(designAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo(resTruncatedValue)

    val assetSet = ResourceAssetSet(resName, listOf(designAsset))
    val assetSetData = valueDataProvider.getAssetSetData(assetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(resValue)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }

  @Test
  fun testStringArrayDataProvider() {
    val resName = "string_array"
    val fileName = "arrays.xml"
    val resValue = "item 1, item 2, item 3"
    val resTruncatedValue = "item 1, item 2, i..."

    val stringArray = StudioResourceRepositoryManager.getModuleResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.RES_AUTO, ResourceType.ARRAY).values().first { it.name == "string_array" }
    val designAsset = Asset.fromResourceItem(stringArray) as DesignAsset

    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(stringArray.resourceValue)

    val valueDataProvider = ValueAssetDataProvider(resourceResolver)

    val assetData = valueDataProvider.getAssetData(designAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo(resTruncatedValue)

    val assetSet = ResourceAssetSet(resName, listOf(designAsset))
    val assetSetData = valueDataProvider.getAssetSetData(assetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(resValue)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }

  @Test
  fun testIntegerArrayDataProvider() {
    val resName = "integer_array"
    val fileName = "arrays.xml"
    val resValue = "1, 2, 3"

    val stringArray = StudioResourceRepositoryManager.getModuleResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.RES_AUTO, ResourceType.ARRAY).values().first { it.name == "integer_array" }
    val designAsset = Asset.fromResourceItem(stringArray) as DesignAsset

    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(stringArray.resourceValue)

    val valueDataProvider = ValueAssetDataProvider(resourceResolver)

    val assetData = valueDataProvider.getAssetData(designAsset)
    assertThat(assetData.title).isEqualTo("default")
    assertThat(assetData.subtitle).isEqualTo(fileName)
    assertThat(assetData.metadata).isEqualTo(resValue)

    val assetSet = ResourceAssetSet(resName, listOf(designAsset))
    val assetSetData = valueDataProvider.getAssetSetData(assetSet)
    assertThat(assetSetData.title).isEqualTo(resName)
    assertThat(assetSetData.subtitle).isEqualTo(resValue)
    assertThat(assetSetData.metadata).isEqualTo("1 version")
  }
}

private fun createFakeDesignAsset(name: String, type: ResourceType): DesignAsset {
  val asset = DesignAsset(MockVirtualFile(name), emptyList(), type)
  ResourceFile.createSingle(File("source"), asset.resourceItem as ResourceMergerItem, "")
  return asset
}