/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Image
import javax.swing.*

/**
 * A JList that display [DesignAssetSet].
 */
class DesignAssetsList(private val browserViewModel: DesignAssetExplorer)
  : JList<DesignAssetSet>(browserViewModel.designAssetListModel) {

  var itemMargin = 16
  private val itemBorderWidth = 1

  init {
    layoutOrientation = JList.HORIZONTAL_WRAP
    visibleRowCount = 0
    cellRenderer = ListCellRenderer<DesignAssetSet>
    { list, assetSet, index, isSelected, cellHasFocus ->
      getDesignAssetView(
          browserViewModel.getPreview(assetSet.getHighestDensityAsset()),
          assetSet.name,
          isSelected,
          browserViewModel.getStatusLabel(assetSet)
      )
    }
  }

  private fun getDesignAssetView(
      preview: Image,
      name: String,
      selected: Boolean = false,
      statusLabel: String
  ): JPanel {
    val panel = JPanel(BorderLayout())
    panel.border = if (selected) {
      panel.isOpaque = true
      panel.background = UIUtil.getListUnfocusedSelectionBackground()
      val emptyBorderWidth = itemMargin - itemBorderWidth
      BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIUtil.getListSelectionBackground(), itemBorderWidth, true),
          BorderFactory.createEmptyBorder(emptyBorderWidth, emptyBorderWidth, emptyBorderWidth, emptyBorderWidth))
    }
    else {
      panel.isOpaque = false
      BorderFactory.createEmptyBorder(itemMargin, itemMargin, itemMargin, itemMargin)
    }

    val icon = JLabel(ImageIcon(preview))
    icon.border = BorderFactory.createEmptyBorder(18, 18, 18, 18)
    panel.add(icon)
    panel.add(JLabel(name, JLabel.CENTER), BorderLayout.SOUTH)
    panel.add(JLabel(statusLabel), BorderLayout.NORTH)
    return panel
  }
}

/**
 * Interface for classes that provide [DesignAsset] for [com.android.tools.idea.resourceExplorer.view.DesignAssetsList]
 */
interface DesignAssetExplorer {
  fun getPreview(asset: DesignAsset): Image
  fun getStatusLabel(assetSet: DesignAssetSet): String
  val designAssetListModel: ListModel<DesignAssetSet>
}