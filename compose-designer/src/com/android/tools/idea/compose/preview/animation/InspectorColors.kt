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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.common.canvasTooltipBackground
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object InspectorColors {

  /** Color of the line. */
  val LINE_COLOR = ColorUtil.withAlpha(JBColor(0xa6bcc9, 0x8da9ba), 0.7)

  val LINE_CIRCLE_COLOR = ColorUtil.withAlpha(JBColor(0xa6bcc9, 0x8da9ba), 0.9)

  /** Outline color of the line's circle. */
  val LINE_CIRCLE_OUTLINE_COLOR = JBColor(Color.white, JBColor.background())

  val LINE_OUTLINE_COLOR_ACTIVE: Color = UIUtil.getTreeSelectionBorderColor()

  /** List of colors for graphs. */
  val GRAPH_COLORS =
    arrayListOf(
      JBColor(0xa6bcc9, 0x8da9ba),
      JBColor(0xaee3fe, 0x86d5fe),
      JBColor(0xf8a981, 0xf68f5b),
      JBColor(0x89e69a, 0x67df7d),
      JBColor(0xb39bde, 0x9c7cd4),
      JBColor(0xea85aa, 0xe46391),
      JBColor(0x6de9d6, 0x49e4cd),
      JBColor(0xe3d2ab, 0xd9c28c),
      JBColor(0x0ab4ff, 0x0095d6),
      JBColor(0x1bb6a2, 0x138173),
      JBColor(0x9363e3, 0x7b40dd),
      JBColor(0xe26b27, 0xc1571a),
      JBColor(0x4070bf, 0x335a99),
      JBColor(0xc6c54e, 0xadac38),
      JBColor(0xcb53a3, 0xb8388e),
      JBColor(0x3d8eff, 0x1477ff)
    )

  /** List of semi-transparent colors for graphs. */
  val GRAPH_COLORS_WITH_ALPHA = GRAPH_COLORS.map { ColorUtil.withAlpha(it, 0.7) }

  /** Background color for the timeline. */
  val TIMELINE_BACKGROUND_COLOR = JBColor(Gray._245, JBColor.background())

  /** Background color for the timeline for frozen elements. */
  val TIMELINE_FROZEN_BACKGROUND_COLOR = JBColor(Gray._234, Gray._58)

  /** Color of the ticks for the timeline. */
  val TIMELINE_TICK_COLOR = JBColor(Gray._223, Gray._50)

  /** Color of the horizontal ticks for the timeline. */
  val TIMELINE_HORIZONTAL_TICK_COLOR = JBColor.border()

  /** Color of the vertical line showing the freeze position. */
  val FREEZE_LINE_COLOR = JBColor(Gray._176, Gray._176)

  val BOXED_LABEL_BACKGROUND = JBColor(Gray._225, UIUtil.getToolTipActionBackground())

  val BOXED_LABEL_OUTLINE = Gray._194

  val BOXED_LABEL_NAME_COLOR = UIUtil.getContextHelpForeground()
  val BOXED_LABEL_VALUE_COLOR = UIUtil.getLabelDisabledForeground()

  val TOOLTIP_BACKGROUND_COLOR = canvasTooltipBackground
  val TOOLTIP_TEXT_COLOR = JBColor.foreground()
}
