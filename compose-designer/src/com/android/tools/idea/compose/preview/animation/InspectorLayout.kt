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

import android.annotation.SuppressLint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Stroke

/**
 * Layout sizes for Animation Inspector. All sizes are in px.
 * TODO Check how layout is resized and scale it appropriately if needed.
 */
@SuppressLint("JbUiStored")
object InspectorLayout {

  init {
    updateSizes()
    JBUIScale.addUserScaleChangeListener {
      updateSizes()
    }
  }

  private fun updateSizes() {
    boxedLabelOffset = JBUI.scale(6)
    labelOffset = JBUI.scale(10)
    dashedStroke = BasicStroke(JBUI.scale(1).toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f,
                               floatArrayOf(JBUI.scale(3).toFloat()), 0f)
    simpleStroke = BasicStroke(JBUI.scale(1).toFloat())
    freezeLineStroke = BasicStroke(JBUI.scale(3).toFloat())
  }

  /** Size of the outline padding. */
  private const val OUTLINE_PADDING = 1

  fun outlinePaddingScaled() = JBUI.scale(OUTLINE_PADDING)

  /** Height of the line. */
  private const val LINE_HEIGHT = 8

  /** Scaled height of the line. */
  fun lineHeightScaled() = JBUI.scale(LINE_HEIGHT)

  /** Scaled half height of the line. */
  fun lineHalfHeightScaled(): Int = JBUI.scale(LINE_HEIGHT / 2)

  /** Height of one row for line. */
  const val TIMELINE_LINE_ROW_HEIGHT = 75

  /** Height of one row for line. */
  fun timelineLineRowHeightScaled() = JBUI.scale(TIMELINE_LINE_ROW_HEIGHT)

  /** Height of one row for unsupported animation. */
  const val UNSUPPORTED_ROW_HEIGHT = 30

  /** Height of one row for curve. */
  const val TIMELINE_CURVE_ROW_HEIGHT = 75

  /** Scaled height of one row for curve. */
  fun timelineCurveRowHeightScaled() = JBUI.scale(TIMELINE_CURVE_ROW_HEIGHT)

  /** Offset from the top of the row to the curve. */
  const val CURVE_TOP_OFFSET = 10

  /** Offset from the bottom of the row to the curve. */
  private const val CURVE_BOTTOM_OFFSET = 42

  /** Scaled offset from the bottom of the row to the curve. */
  fun curveBottomOffset() = JBUI.scale(CURVE_BOTTOM_OFFSET)

  /**
   * Height of the animation inspector timeline header, i.e. Transition Properties panel title and timeline labels.
   */
  const val TIMELINE_HEADER_HEIGHT = 25

  /**
   * Scaled height of the animation inspector timeline header, i.e. Transition Properties panel title and timeline labels.
   */
  fun timelineHeaderHeightScaled() = JBUI.scale(TIMELINE_HEADER_HEIGHT)

  /** Vertical margin for labels.  */
  private const val TIMELINE_LABEL_VERTICAL_MARGIN = 5

  /** Scaled vertical margin for labels. */
  fun timelineLabelVerticalMarginScaled() = JBUI.scale(TIMELINE_LABEL_VERTICAL_MARGIN)

  /** Number of ticks per label in the timeline. */
  const val TIMELINE_TICKS_PER_LABEL = 5

  /** Offset between components. */
  var boxedLabelOffset = JBUI.scale(6)

  /** Size of the color box for [ComposeUnit.Color] property. */
  val boxedLabelColorBoxSize = JBUI.size(10)
  val boxedLabelColorBoxArc = JBUI.size(4)

  /** Outline offset of the color box for [ComposeUnit.Color] property. */
  const val BOXED_LABEL_COLOR_OUTLINE_OFFSET = 1

  /** Label offset from the curve. */
  var labelOffset = JBUI.scale(10)
    private set

  /** Height of the [BottomPanel]. */
  const val BOTTOM_PANEL_HEIGHT = 25

  lateinit var dashedStroke: Stroke
    private set

  lateinit var simpleStroke: Stroke
    private set

  /** Vertical line showing the freeze position. */
  lateinit var freezeLineStroke: Stroke
    private set
}