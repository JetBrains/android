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
package com.android.tools.idea.ui.resourcechooser

import com.android.tools.adtui.common.borderLight
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.rendering.DefaultIconProvider
import com.android.tools.idea.ui.resourcemanager.widget.ChessBoardPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * [ListCellRenderer] to display resources in a compact space.
 *
 * @param cellHeight The expected height of each item rendered in the list
 */
class CompactResourceListCellRenderer(private val assetPreviewManager: AssetPreviewManager,
                                      cellHeight: Int) : ListCellRenderer<ResourceAssetSet> {
  private val widget = AssetSetWidget(cellHeight)
  override fun getListCellRendererComponent(list: JList<out ResourceAssetSet>,
                                            value: ResourceAssetSet,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val assetToRender = value.getHighestDensityAsset()
    val thumbnailSize = widget.getThumbnailSize()
    val iconProvider = assetPreviewManager.getPreviewProvider(assetToRender.type)
    val icon = if (iconProvider is DefaultIconProvider) {
      null
    }
    else {
      iconProvider.getIcon(assetToRender,
                           thumbnailSize.width,
                           thumbnailSize.height,
                           list,
                           { list.getCellBounds(index, index)?.let(list::repaint) },
                           { index in list.firstVisibleIndex..list.lastVisibleIndex })
    }
    widget.updateWidget(
      icon,
      value.name,
      isSelected,
      cellHasFocus
    )
    return widget
  }
}

/**
 * The widget used to display items in the [CompactResourceListCellRenderer], takes an icon and a text through [updateWidget].
 *
 * Has a fixed height given by [cellHeight], so that when calling [getThumbnailSize] it can return the expected dimension in which it will
 * display the given icon from [updateWidget].
 *
 * The icon is displayed with a chessboard background so that it can be properly viewed if it has any transparencies.
 *
 * If no icon is given then it just displays the text aligned to the left.
 */
private class AssetSetWidget(private val cellHeight: Int) : JPanel(BorderLayout()) {
  private val mainLabel = JBLabel("").apply {
    isOpaque = false
    border = JBEmptyBorder(0, 5, 0, 0)
  }
  private val iconLabel = JBLabel().apply {
    isOpaque = false
  }
  private val iconWrapper = ChessBoardPanel(cellHeight.div(6).coerceAtLeast(2)).apply {
    isOpaque = false
    add(iconLabel)
    border = JBUI.Borders.merge(JBUI.Borders.customLine(borderLight), JBUI.Borders.empty(4, 0), true)
  }

  init {
    val mainContentPanel = JPanel(BorderLayout()).apply {
      isOpaque = false
      add(iconWrapper, BorderLayout.WEST)
      add(mainLabel)
      border = JBEmptyBorder(0, 4, 0, 0)
    }
    add(mainContentPanel, BorderLayout.WEST)
    iconWrapper.preferredSize = JBDimension(cellHeight - 8, cellHeight - 8)
    background = PICKER_BACKGROUND_COLOR
  }

  fun getThumbnailSize(): Dimension {
    return iconWrapper.preferredSize
  }

  fun updateWidget(icon: Icon?, title: String, isSelected: Boolean, isFocused: Boolean) {
    if (icon != null) {
      iconWrapper.isVisible = true
      iconWrapper.preferredSize = JBDimension(cellHeight - 8, cellHeight - 8)
    }
    else {
      iconWrapper.isVisible = false
      iconWrapper.preferredSize = JBDimension(0, 0)
    }
    iconLabel.icon = icon
    mainLabel.text = title
    mainLabel.foreground = UIUtil.getTreeForeground(isSelected, isFocused)
    background = if (isSelected) UIUtil.getTreeSelectionBackground(isFocused) else PICKER_BACKGROUND_COLOR
  }
}