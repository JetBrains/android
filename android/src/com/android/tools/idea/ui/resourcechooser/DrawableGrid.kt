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
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.io.File
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListModel


private const val ITEM_BORDER_WIDTH = 4
private const val ITEM_SELECTED_BORDER_WIDTH = 2
private const val DEFAULT_CACHE_SIZE = 128L
private val DEFAULT_IMAGE_SIZE = JBUI.scale(48)
private val EMPTY_ICON_COLOR = JBColor(Color(0xAA, 0xAA, 0xAA, 0x33),
                                       Color(0xAA, 0xAA, 0xAA, 0x33))
private val ITEM_BORDER = JBUI.Borders.empty(ITEM_BORDER_WIDTH)
private val ITEM_BORDER_SELECTED = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(ITEM_BORDER_WIDTH - ITEM_SELECTED_BORDER_WIDTH),
  JBUI.Borders.customLine(UIUtil.getListBackground(true), ITEM_SELECTED_BORDER_WIDTH))

/**
 * Component that displays [ResourceValue] in a grid.
 */
open class DrawableGrid(val module: Module,
                        model: ListModel<ResourceValue>,
                        imageSize: Int = DEFAULT_IMAGE_SIZE,
                        private val cacheSize: Long = DEFAULT_CACHE_SIZE)
  : JList<ResourceValue>(model) {

  init {
    layoutOrientation = HORIZONTAL_WRAP
    setImageSize(imageSize)
  }

  override fun setSelectionInterval(anchor: Int, lead: Int) {
    if (lead >= 0 && lead < model.size && model.getElementAt(lead) == null) {
      return
    }
    super.setSelectionInterval(anchor, lead)
  }

  private fun setImageSize(drawableSize: Int) {
    fixedCellWidth = drawableSize + ITEM_BORDER_WIDTH * 2
    fixedCellHeight = fixedCellWidth
    cellRenderer = DrawableCellRenderer(module, drawableSize, cacheSize)
  }

  fun resetCache() {
    (cellRenderer as DrawableCellRenderer).resetCache()
  }
}

internal class DrawableCellRenderer(private val module: Module,
                                    imageSize: Int,
                                    cacheSize: Long = DEFAULT_CACHE_SIZE)
  : ListCellRenderer<ResourceValue> {

  private val imageDimension = Dimension(imageSize, imageSize)
  private var emptyIcon = ColorIcon(imageSize, EMPTY_ICON_COLOR)
  private val disabledComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
  private val cache = CacheBuilder.newBuilder()
    .softValues()
    .maximumSize(cacheSize)
    .build<ResourceValue, Icon>()
  private val label = JLabel().apply {
    horizontalAlignment = JLabel.CENTER
  }

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
    label.border = if (isSelected && label.isEnabled) ITEM_BORDER_SELECTED else ITEM_BORDER

    if (value == null || module.isDisposed) {
      label.icon = emptyIcon
      label.disabledIcon = emptyIcon
      return label
    }
    else {
      val icon = cache.getIfPresent(value)
      if (icon != null) {
        label.disabledIcon = if (icon is ImageIcon) createDisabledIcon(icon) else null
        label.icon = icon
        return label
      }
      else {
        label.icon = emptyIcon
        label.disabledIcon = emptyIcon
      }
    }

    val file = VfsUtil.findFileByIoFile(File(value.value), true) ?: return label

    DesignAssetRendererManager.getInstance()
      .getViewer(file)
      .getImage(file, module, imageDimension)
      .whenCompleteAsync(BiConsumer { image, ex ->
        if (ex == null) {
          cache.put(value, ImageIcon(image))
          val cellBounds = list.getCellBounds(index, index)
          if (cellBounds != null) {
            list.repaint(cellBounds)
          }
          else {
            list.repaint()
          }
        }
      }, EdtExecutorService.getInstance())

    return label
  }

  fun resetCache() {
    cache.cleanUp()
    label.icon = emptyIcon
    label.disabledIcon = emptyIcon
  }
}