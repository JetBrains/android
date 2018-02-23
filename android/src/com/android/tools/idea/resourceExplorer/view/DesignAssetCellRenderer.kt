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
package com.android.tools.idea.resourceExplorer.view

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.*
import javax.swing.*

private val MAIN_CELL_BORDER = BorderFactory.createEmptyBorder(10, 30, 10, 30)
private val CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(10, 0, 10, 0),
  BorderFactory.createLineBorder(Gray.x41, 2)
)

/**
 * Base renderer for the asset list.
 */
abstract class DesignAssetCellRenderer : ListCellRenderer<ResourceValue> {

  var title: String
    get() = titleLabel.text
    set(value) {
      titleLabel.text = value
    }

  var subtitle: String
    get() = subtitleLabel.text
    set(value) {
      subtitleLabel.text = value
    }

  private val mainPanel = JPanel(BorderLayout()).apply {
    border = MAIN_CELL_BORDER
  }

  private var contentWrapper: JComponent = JPanel(BorderLayout()).apply {
    border = CONTENT_CELL_BORDER
  }

  private val bottomPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
  }
  private val titleLabel = JLabel()
  private val subtitleLabel = JLabel()

  init {
    mainPanel.add(contentWrapper)
    mainPanel.add(bottomPanel, BorderLayout.SOUTH)
    with(bottomPanel) {
      add(titleLabel)
      add(subtitleLabel, BorderLayout.SOUTH)
      add(JLabel(StudioIcons.Common.WARNING), BorderLayout.EAST)
    }
  }

  override fun getListCellRendererComponent(
    list: JList<out ResourceValue>,
    value: ResourceValue,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    mainPanel.preferredSize = JBUI.size(list.fixedCellWidth, list.fixedCellHeight)
    contentWrapper.removeAll()
    val content = getContent(value, list.fixedCellWidth, list.fixedCellHeight, isSelected)
    if (content != null) {
      contentWrapper.add(content)
    }
    mainPanel.background = UIUtil.getListBackground(isSelected)
    contentWrapper.background = mainPanel.background

    return mainPanel
  }

  abstract fun getContent(value: ResourceValue, width: Int, height: Int, isSelected: Boolean): JComponent?
}

class ColorResourceCellRenderer(
  private val project: Project,
  private val resourceResolver: ResourceResolver
) : DesignAssetCellRenderer() {
  private val backgroundPanel = ColorPreviewPanel()

  override fun getContent(value: ResourceValue, width: Int, height: Int, isSelected: Boolean): JComponent? {
    title = value.name

    // TODO compute in background
    val colors = resourceResolver.resolveMultipleColors(value, project).toSet().toList()
    backgroundPanel.colorList = colors
    backgroundPanel.colorCodeLabel.text = if (colors.size == 1) {
      "#${ColorUtil.toHex(colors.first())}"
    } else {
      ""
    }
    return backgroundPanel
  }

  inner class ColorPreviewPanel : JPanel(BorderLayout()) {
    internal var colorList = emptyList<Color>()
    internal val colorCodeLabel = JLabel()

    init {
      add(colorCodeLabel, BorderLayout.SOUTH)
    }

    override fun paintComponent(g: Graphics) {
      if (colorList.isEmpty()) return

      val splitSize = width / colorList.size
      for (i in 0 until colorList.size) {
        g.color = colorList[i]
        g.fillRect(i * splitSize, 0, splitSize, height)
      }
    }
  }
}