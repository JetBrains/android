/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.ide.common.util.AssetUtil
import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.common.MaterialSymbolsUrlProvider
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.npw.assetstudio.assets.MaterialSymbolsVirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [TableCellRenderer] used in [IconPickerDialog], uses a [JBLabel] to render the icons used in the picker with the correct Look and Feel.
 *
 * This CellRenderer expects [MaterialSymbolsVirtualFile]s in the [JTable] model.
 */
class IconPickerCellLayoutRenderer(
  coroutineScope: CoroutineScope,
  getMetadata: () -> MaterialIconsMetadata,
  urlProvider: MaterialSymbolsUrlProvider,
  vdIconLoader: suspend (SymbolConfiguration, MaterialMetadataIcon, MaterialIconsMetadata, MaterialSymbolsUrlProvider) -> VdIcon?,
) : TableCellRenderer {

  private val label = IconPickerCellComponentXML(coroutineScope, getMetadata, urlProvider, vdIconLoader)

  override fun getTableCellRendererComponent(
    table: JTable?,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    if (table == null) {
      return JBLabel()
    }
    return label.apply {
      updateComponent(table = table, isSelected = isSelected, isFocused = hasFocus, value = value, row = row, column = column)
    }
  }
}

private const val ARC_SIZE = 5
private const val BORDER_SIZE = 1
private const val TEXT_HEIGHT = 16
private const val PADDING_BOTTOM = 8

private class MaterialSymbolVdIconWrapper(private val vdIcon: VdIcon, private val iconWidth: Int, private val iconHeight: Int) : Icon {
  override fun getIconWidth(): Int = iconWidth

  override fun getIconHeight(): Int = iconHeight

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    if (g !is Graphics2D || c == null) return
    val size = minOf(iconWidth, iconHeight)
    val image = vdIcon.renderIcon(size, size) ?: return
    val coloredImage = VdIcon.adjustIconColor(c, image)
    val rect = Rectangle(x, y, iconWidth, iconHeight)
    AssetUtil.drawCenterInside(g, coloredImage, rect)
  }
}

private object MaterialSymbolVdIconCache {
  private val iconCache = ConcurrentHashMap<Pair<SymbolConfiguration, String>, VdIcon>()
  private val pendingLoads = ConcurrentHashMap.newKeySet<Pair<SymbolConfiguration, String>>()

  fun getIcon(symbolConfiguration: SymbolConfiguration, symbolName: String): VdIcon? {
    return iconCache[symbolConfiguration to symbolName]
  }

  fun loadIconAsync(
    coroutineScope: CoroutineScope,
    symbolConfiguration: SymbolConfiguration,
    metadataIcon: MaterialMetadataIcon,
    iconsMetadata: MaterialIconsMetadata,
    urlProvider: MaterialSymbolsUrlProvider,
    vdIconLoader: suspend (SymbolConfiguration, MaterialMetadataIcon, MaterialIconsMetadata, MaterialSymbolsUrlProvider) -> VdIcon?,
    onLoaded: () -> Unit,
  ) {
    val key = symbolConfiguration to metadataIcon.name
    if (iconCache.containsKey(key) || !pendingLoads.add(key)) return

    coroutineScope.launch {
      val vdIcon =
        try {
          withContext(Dispatchers.IO) { vdIconLoader(symbolConfiguration, metadataIcon, iconsMetadata, urlProvider) }
        } catch (e: Throwable) {
          null
        } finally {
          pendingLoads.remove(key)
        }
      if (vdIcon != null) {
        iconCache[key] = vdIcon
        withContext(Dispatchers.Main) { onLoaded() }
      }
    }
  }
}

private class IconPickerCellComponentXML(
  private val coroutineScope: CoroutineScope,
  private val getMetadata: () -> MaterialIconsMetadata,
  private val urlProvider: MaterialSymbolsUrlProvider,
  private val vdIconLoader:
    suspend (SymbolConfiguration, MaterialMetadataIcon, MaterialIconsMetadata, MaterialSymbolsUrlProvider) -> VdIcon?,
) : JBLabel() {
  /** Background color for selected icons */
  private val backgroundFocusedColor = JBColor(Color(0x1a1886f7, true), Color(0x1a9ccdff, true))

  private val selectedFocusedBorder = IdeBorderFactory.createRoundedBorder(JBUI.scale(ARC_SIZE), JBUI.scale(BORDER_SIZE))

  init {
    isOpaque = false
    border = JBUI.Borders.emptyBottom(PADDING_BOTTOM)
    font = JBUI.Fonts.miniFont()

    horizontalTextPosition = CENTER
    verticalTextPosition = BOTTOM
    horizontalAlignment = CENTER
  }

  private var isSelected: Boolean = false

  private var isFocused: Boolean = false

  fun updateComponent(table: JTable, isSelected: Boolean, isFocused: Boolean, value: Any?, row: Int, column: Int) {
    var displayName = ""
    if (value is MaterialSymbolsVirtualFile) {
      this.isSelected = isSelected
      this.isFocused = isFocused
      val cellRect = table.getCellRect(row, column, false)
      val iconAreaWidth = cellRect.width
      val iconAreaHeight = cellRect.height - (TEXT_HEIGHT + PADDING_BOTTOM)
      val cachedVdIcon = MaterialSymbolVdIconCache.getIcon(value.symbolConfiguration, value.metadata.name)
      if (cachedVdIcon != null) {
        icon = MaterialSymbolVdIconWrapper(cachedVdIcon, iconAreaWidth, iconAreaHeight)
      } else {
        icon = null
        MaterialSymbolVdIconCache.loadIconAsync(
          coroutineScope,
          value.symbolConfiguration,
          value.metadata,
          getMetadata(),
          urlProvider,
          vdIconLoader,
        ) {
          val currentRect = table.getCellRect(row, column, false)
          if (table.visibleRect.intersects(currentRect)) {
            table.repaint(currentRect)
          }
        }
      }
      displayName = value.displayName
    } else {
      this.isSelected = false
      this.isFocused = false
      icon = null
    }
    text = displayName
    AccessibleContextUtil.setName(this, displayName)
  }

  override fun paintComponent(g: Graphics?) {
    if (isFocused || isSelected) {
      if (g is Graphics2D) {
        val oldAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = backgroundFocusedColor
        val offset = JBUI.scale(BORDER_SIZE)
        g.fillRoundRect(0, 0, width - offset, height - offset, JBUI.scale(ARC_SIZE), JBUI.scale(ARC_SIZE))

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
        if (isFocused) {
          selectedFocusedBorder.apply {
            setColor(UIUtil.getTreeSelectionBackground(isFocused))
            paintBorder(this@IconPickerCellComponentXML, g, 0, 0, width, height)
          }
        }
      }
    }
    super.paintComponent(g)
  }
}
