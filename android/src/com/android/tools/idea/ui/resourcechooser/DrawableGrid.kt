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
package com.android.tools.idea.ui.resourcechooser

import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.io.File
import javax.swing.*


private const val ITEM_BORDER_WIDTH = 4
private const val ITEM_SELECTED_BORDER_WIDTH = 2
private const val DEFAULT_CACHE_SIZE = 12L
private val DEFAULT_IMAGE_SIZE = JBUI.scale(48)
private val EMPTY_ICON_COLOR = JBColor.foreground()
private val ITEM_BORDER = JBUI.Borders.empty(ITEM_BORDER_WIDTH)
private val ITEM_BORDER_SELECTED = JBUI.Borders.merge(
  JBUI.Borders.customLine(UIUtil.getListBackground(true), ITEM_SELECTED_BORDER_WIDTH),
  JBUI.Borders.empty(ITEM_BORDER_WIDTH - ITEM_SELECTED_BORDER_WIDTH),
  true)

/**
 * Component that displays [ResourceValue] in a grid.
 */
class DrawableGrid(val module: Module,
                   model: ListModel<ResourceValue>)
  : JList<ResourceValue>(model) {


  var drawableSize = DEFAULT_IMAGE_SIZE
    set(value) {
      fixedCellWidth = value + ITEM_BORDER_WIDTH * 2
      fixedCellHeight = fixedCellWidth
      cellRenderer = DrawableCellRenderer(module, drawableSize)
    }

  init {
    layoutOrientation = HORIZONTAL_WRAP
  }

  fun resetCache() {
    (cellRenderer as DrawableCellRenderer).resetCache()
  }
}

internal class DrawableCellRenderer(private val module: Module,
                                    private val imageSize: Int)
  : ListCellRenderer<ResourceValue> {

  private var emptyIcon = ColorIcon(imageSize, EMPTY_ICON_COLOR)
  private val disabledComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
  private val cache = CacheBuilder.newBuilder()
    .maximumSize(DEFAULT_CACHE_SIZE)
    .build<ResourceValue, Icon>()
  private val label = JLabel()

  private fun createDisabledIcon(imageIcon: ImageIcon) = object : Icon by imageIcon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
      (g as Graphics2D).composite = disabledComposite
      imageIcon.paintIcon(c, g, x, y)
    }
  }

  override fun getListCellRendererComponent(list: JList<out ResourceValue>,
                                            value: ResourceValue?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    label.isEnabled = list.isEnabled
    label.border = if (isSelected) ITEM_BORDER_SELECTED else ITEM_BORDER

    if (value == null || module.isDisposed) {
      label.icon = emptyIcon
      return label
    }
    else {
      val icon = cache.getIfPresent(value)
      if (icon != null) {
        label.disabledIcon = if (icon is ImageIcon) createDisabledIcon(icon) else null
        label.icon = icon
        return label
      }
    }

    val file = VfsUtil.findFileByIoFile(File(value.value), true)
    if (file == null) {
      label.icon = emptyIcon
      return label
    }

    val image = DesignAssetRendererManager.getInstance()
      .getViewer(file)
      .getImage(file, module, JBUI.size(imageSize))

    image.addListener(Runnable {
      cache.put(value, ImageIcon(image.get()))
      list.repaint(list.getCellBounds(index, index))
    }, EdtExecutor.INSTANCE)
    return label
  }

  fun resetCache() {
    cache.cleanUp()
    label.icon = emptyIcon
  }
}