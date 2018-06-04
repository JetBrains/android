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
package com.android.tools.idea.ui.resourcechooser.preview

import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.res.getDrawableResources
import com.android.tools.idea.ui.resourcechooser.DrawableGrid
import com.android.tools.idea.ui.resourcechooser.ResourceChooserItem
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*
import kotlin.properties.Delegates

private const val IMAGE_SIZE = 64
private const val SAMPLE_DATA_DOC_URL = "https://developer.android.com/studio/write/tool-attributes.html#design-time_view_attributes"
private const val INFO_TEXT = "This sample resource will be used at design-time."
private const val LEARN_MORE_LINK_TEXT = "Learn More."
private val learnMoreLinkListener = LinkListener<String> { _, _ ->
  BrowserUtil.browse(SAMPLE_DATA_DOC_URL)
}

/**
 * Offset to left align siblings of the grid with the cell of the grid
 */
private val GRID_CELL_BORDER_OFFSET = JBUI.Borders.emptyLeft(4)
private val CHECKBOX_ALIGNMENT_OFFSET = JBUI.Borders.emptyLeft(1)

private val INFO_TEXT_PANEL_BORDER = JBUI.Borders.emptyTop(12)

private val CONTENT_PADDING = JBUI.Borders.empty(18)

/**
 * Panel for [com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog] to display
 * and select values of a [com.android.tools.idea.res.SampleDataResourceItem].
 */
class SampleDrawablePanel(val module: Module) : JPanel(BorderLayout()) {

  private val listModel = DefaultListModel<ResourceValue>()
  private var currentItem: ResourceChooserItem.SampleDataItem? = null
  private var selectedIndex by Delegates.observable(-1, { _, old, new -> if (old != new) selectItem(new) })
  private val resourceNameLabel = JLabel()

  private val drawableGrid = DrawableGrid(module, listModel, IMAGE_SIZE).apply {
    isOpaque = false
    isEnabled = false
    visibleRowCount = 0
    minimumSize = JBUI.size(IMAGE_SIZE)
    addListSelectionListener { this@SampleDrawablePanel.selectedIndex = selectedIndex }
  }

  private val allCheckbox = JBCheckBox("Use as set", true).apply {
    alignmentX = LEFT_ALIGNMENT
    border = CHECKBOX_ALIGNMENT_OFFSET
    addActionListener { selectedIndex = if (isSelected) -1 else 0 }
  }

  init {
    border = CONTENT_PADDING
    alignmentX = JPanel.LEFT_ALIGNMENT

    add(allCheckbox, BorderLayout.NORTH)

    add(JPanel(VerticalFlowLayout(0, 8)).apply {
      val borderLeft = GRID_CELL_BORDER_OFFSET // Used to left align elements with the grid
      add(drawableGrid)
      add(JLabel("Resource name").apply {
        border = borderLeft
        font = font.deriveFont(Font.BOLD)
      })
      add(resourceNameLabel.apply {
        border = borderLeft
      })
    })

    add(JPanel().apply {
      border = INFO_TEXT_PANEL_BORDER
      add(JLabel(INFO_TEXT, StudioIcons.Common.INFO, JLabel.LEFT))
      add(LinkLabel<String>(LEARN_MORE_LINK_TEXT, null, learnMoreLinkListener))
    }, BorderLayout.SOUTH)
  }

  private fun selectItem(selectedIndex: Int) {
    currentItem?.setValueIndex(selectedIndex)
    val useAll = selectedIndex < 0
    val listEnabled = !useAll
    if (drawableGrid.selectedIndex != selectedIndex || drawableGrid.isEnabled != listEnabled) {
      drawableGrid.isEnabled = listEnabled
      drawableGrid.selectedIndex = selectedIndex
    }
    allCheckbox.isSelected = useAll
    resourceNameLabel.text = currentItem?.resourceUrl.orEmpty()
  }

  fun select(item: ResourceChooserItem.SampleDataItem) {
    currentItem = item
    listModel.removeAllElements()
    item.resourceItem.getDrawableResources().forEach {
      listModel.addElement(it)
    }
    // When changing the item, we want to use all values, thus we set the selected item to -1
    selectItem(-1)
  }
}
