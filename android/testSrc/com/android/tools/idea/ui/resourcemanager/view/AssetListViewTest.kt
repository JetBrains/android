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
package com.android.tools.idea.ui.resourcemanager.view

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.ui.speedSearch.SpeedSearch
import org.junit.Test
import javax.swing.JList


class AssetListViewTest {

  @Test
  fun default() {
    val assetListView = AssetListView(emptyList())
    assertThat(assetListView.fixedCellHeight).isNotEqualTo(-1)
    assertThat(assetListView.fixedCellWidth).isNotEqualTo(-1)
    assertThat(assetListView.isGridMode).isFalse()
    assertThat(assetListView.thumbnailWidth).isGreaterThan(0)
    assertThat(assetListView.layoutOrientation).isEqualTo(JList.VERTICAL)
    assertThat(assetListView.visibleRowCount).isEqualTo(0)
  }


  @Test
  fun listMode() {
    val assetListView = AssetListView(emptyList())
    assetListView.isGridMode = false
    assertThat(assetListView.fixedCellHeight).isNotEqualTo(-1)
    assertThat(assetListView.fixedCellWidth).isNotEqualTo(-1)
    assertThat(assetListView.isGridMode).isFalse()
    assertThat(assetListView.thumbnailWidth).isGreaterThan(0)
    assertThat(assetListView.layoutOrientation).isEqualTo(JList.VERTICAL)
    assertThat(assetListView.visibleRowCount).isEqualTo(0)
  }

  @Test
  fun gridMode() {
    val assetListView = AssetListView(emptyList())
    assetListView.isGridMode = true
    assertThat(assetListView.fixedCellHeight).isNotEqualTo(-1)
    assertThat(assetListView.fixedCellWidth).isNotEqualTo(-1)
    assertThat(assetListView.isGridMode).isTrue()
    assertThat(assetListView.thumbnailWidth).isGreaterThan(0)
    assertThat(assetListView.layoutOrientation).isEqualTo(JList.HORIZONTAL_WRAP)
    assertThat(assetListView.visibleRowCount).isEqualTo(0)
  }

  @Test
  fun previewSize() {
    val assetListView = AssetListView(emptyList())
    val size = assetListView.thumbnailWidth
    val cellWidth = assetListView.fixedCellWidth
    val cellHeight = assetListView.fixedCellHeight

    assetListView.thumbnailWidth = size * 2
    assertThat(assetListView.thumbnailWidth).isEqualTo(size * 2)
    assertThat(assetListView.fixedCellHeight).isGreaterThan(cellHeight)
    assertThat(assetListView.fixedCellWidth).isGreaterThan(cellWidth)
    assertThat(assetListView.isGridMode).isFalse()
    assertThat(assetListView.layoutOrientation).isEqualTo(JList.VERTICAL)
    assertThat(assetListView.visibleRowCount).isEqualTo(0)
  }

  @Test
  fun filtering() {
    val speedSearch = SpeedSearch(true)
    val assetList = listOf(
      createMockAssetSet("abc"),
      createMockAssetSet("def"),
      createMockAssetSet("ad"))
    val assetListView = AssetListView(assetList, speedSearch)
    assertThat(assetListView.model.size).isEqualTo(3)
    speedSearch.updatePattern("a")
    assetListView.refilter()
    assertThat(assetListView.model.size).isEqualTo(2)
    assertThat(assetListView.model.getElementAt(0).name).isEqualTo("abc")
    assertThat(assetListView.model.getElementAt(1).name).isEqualTo("ad")
    speedSearch.updatePattern("abc")
    assetListView.refilter()
    assertThat(assetListView.model.size).isEqualTo(1)
    assertThat(assetListView.model.getElementAt(0).name).isEqualTo("abc")
    speedSearch.updatePattern("")
    assetListView.refilter()
    assertThat(assetListView.model.size).isEqualTo(3)
  }

  private fun createMockAssetSet(name: String) =
    DesignAssetSet(name, listOf(
      DesignAsset(MockVirtualFile("$name.png"), emptyList(), ResourceType.DRAWABLE)
    ))



}