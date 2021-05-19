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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.chart.hchart.HRenderer
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.DataVisualizationColors.getColor
import com.android.tools.adtui.common.DataVisualizationColors.getFontColor
import com.android.tools.adtui.common.DataVisualizationColors.toGrayscale
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

class HeapSetNodeHRenderer : HRenderer<ClassifierSetHNode?> {
  override fun render(g: Graphics2D,
                      node: ClassifierSetHNode,
                      fullDrawingArea: Rectangle2D,
                      drawingArea: Rectangle2D,
                      isFocused: Boolean,
                      isDeselected: Boolean) {
    // Draw rectangle background
    val index = node.name.hashCode()
    var color: Color = getColor(index, isFocused)
    if (isDeselected && !isFocused) {
      color = toGrayscale(color)
    }
    g.paint = color
    g.fill(drawingArea)

    // Draw text
    var font = g.font
    val restoreFont = font
    val textColor: Color = getFontColor(index)
    g.paint = textColor
    if (node.isFiltered && node.isMatched) {
      font = font.deriveFont(Font.BOLD)
      g.font = font
    }
    else if (node.isFiltered) {
      g.paint = ColorUtil.withAlpha(textColor, .2)
    }
    val marginPadding = 5
    val fontMetrics = g.getFontMetrics(font)
    val availableWidth = drawingArea.width.toFloat() - 2 * marginPadding // Left and right margin
    val text = AdtUiUtils.shrinkToFit(node.name, fontMetrics, availableWidth, 1.0f)
    if (text.isNotEmpty()) {
      val textPositionX = marginPadding + drawingArea.x.toFloat()
      val textPositionY = (drawingArea.y + fontMetrics.ascent).toFloat()
      g.drawString(text, textPositionX, textPositionY)
    }
    g.font = restoreFont
  }
}