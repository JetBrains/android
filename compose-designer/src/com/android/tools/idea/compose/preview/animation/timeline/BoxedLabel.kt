/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.timeline

import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.InspectorColors
import com.android.tools.idea.compose.preview.animation.InspectorLayout.BOXED_LABEL_COLOR_OUTLINE_OFFSET
import com.android.tools.idea.compose.preview.animation.InspectorLayout.boxedLabelColorBoxArc
import com.android.tools.idea.compose.preview.animation.InspectorLayout.boxedLabelColorBoxSize
import com.android.tools.idea.compose.preview.animation.InspectorLayout.boxedLabelOffset
import com.android.tools.idea.compose.preview.animation.TooltipInfo
import com.intellij.util.alsoIfNull
import com.intellij.util.ui.JBFont
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.TextLayout

class BoxedLabel(
  private val componentId: Int,
  private val grouped: Boolean,
  private val position: () -> Point
) {
  fun paint(g: Graphics2D) {
    timelineUnit?.let { paintBoxedLabel(g, it, componentId, grouped, position()) }
  }

  fun getTooltip(point: Point): TooltipInfo? {
    return if (boxRect.contains(point))
      timelineUnit?.let {
        TooltipInfo(
          it.propertyLabel,
          "${if (grouped) it.unit?.toString() else it.unit?.toString(componentId)}"
        )
      }
    else null
  }

  var timelineUnit: ComposeUnit.TimelineUnit? = null
    set(value) {
      field = value
      value.alsoIfNull { boxRect = Rectangle(0, 0, 0, 0) }
    }
  private var boxRect = Rectangle(0, 0, 0, 0)

  /** Paint a label with a box on background. */
  private fun paintBoxedLabel(
    g: Graphics2D,
    timelineUnit: ComposeUnit.TimelineUnit,
    componentId: Int,
    grouped: Boolean,
    point: Point
  ) {
    //       Property label
    //       |       (Optional) Colored box for a [ComposeUnit.Color] properties
    //       |       |    Value of the property
    //       |       |    |
    //       ↓       ↓    ↓
    //    ____________________
    //   /                    \
    //   |  Label :️ ⬜️  value |
    //   \____________________/
    //
    //    Example 1                                   Example 2
    // Example 3
    //    ______________________________________      ___________________________________
    // ___________
    //   /                                      \    /                                   \   /
    //     \
    //   |  Rect property : left ( 1, _ , _, _) |    |  Color : 🟦 blue ( _, _ , 0.3, _) |   |  Dp :
    // 1dp |
    //   \______________________________________/    \___________________________________/
    // \___________/
    //
    //    Example 4 when components are grouped together
    //    ______________________________________
    //   /                                      \
    //   |  Rect property : ( 1 , 2 , 3 , 4)     |
    //   \______________________________________/

    val label = "${timelineUnit.propertyLabel} :  "
    val value =
      if (grouped) timelineUnit.unit?.toString() else timelineUnit.unit?.toString(componentId)
    val color = if (timelineUnit.unit is ComposeUnit.Color) timelineUnit.unit.color else null
    g.font = JBFont.medium()
    val labelLayout = TextLayout(label, g.font, g.fontRenderContext)
    val valueLayout = TextLayout(value, g.font, g.fontRenderContext)
    val textBoxHeight = (labelLayout.bounds.height + boxedLabelOffset * 2).toInt()
    val extraColorOffset =
      if (color != null) boxedLabelColorBoxSize.width() + boxedLabelOffset else 0
    val textBoxWidth =
      (labelLayout.bounds.width +
          valueLayout.bounds.width +
          boxedLabelOffset * 3 +
          extraColorOffset)
        .toInt()
    boxRect = Rectangle(point.x - boxedLabelOffset, point.y, textBoxWidth, textBoxHeight)

    // Background box
    g.color = InspectorColors.BOXED_LABEL_BACKGROUND
    g.fillRoundRect(
      boxRect.x,
      boxRect.y,
      boxRect.width,
      boxRect.height,
      boxedLabelOffset,
      boxedLabelOffset
    )
    // Label
    g.color = InspectorColors.BOXED_LABEL_NAME_COLOR
    val prevAntiAliasHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.drawString(label, point.x, point.y - boxedLabelOffset + textBoxHeight)
    // Colored box
    val xPos = point.x + boxedLabelOffset + labelLayout.bounds.width.toInt()
    color?.let {
      g.color = InspectorColors.BOXED_LABEL_OUTLINE
      g.drawRoundRect(
        xPos,
        point.y + boxedLabelOffset,
        boxedLabelColorBoxSize.width(),
        boxedLabelColorBoxSize.height(),
        boxedLabelColorBoxArc.width(),
        boxedLabelColorBoxArc.height()
      )

      g.color = color
      g.fillRoundRect(
        xPos + BOXED_LABEL_COLOR_OUTLINE_OFFSET,
        point.y + boxedLabelOffset + BOXED_LABEL_COLOR_OUTLINE_OFFSET,
        boxedLabelColorBoxSize.width() - BOXED_LABEL_COLOR_OUTLINE_OFFSET * 2,
        boxedLabelColorBoxSize.height() - BOXED_LABEL_COLOR_OUTLINE_OFFSET * 2,
        boxedLabelColorBoxArc.width(),
        boxedLabelColorBoxArc.height()
      )
    }
    // Value
    g.color = InspectorColors.BOXED_LABEL_VALUE_COLOR
    g.drawString(value, xPos + extraColorOffset, point.y - boxedLabelOffset + textBoxHeight)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAntiAliasHint)
  }
}
