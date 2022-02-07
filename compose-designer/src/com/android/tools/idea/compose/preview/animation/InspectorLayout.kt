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

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Stroke

/**
 * Layout sizes for Animation Inspector. All sizes are in px.
 * TODO Check how layout is resized and scale it appropriately if needed.
 */
object InspectorLayout {

  /** Height of the line. */
  const val LINE_HEIGHT = 8

  /** Half height of the line. */
  const val LINE_HALF_HEIGHT: Int = LINE_HEIGHT / 2

  /** Height of one row for line. */
  const val TIMELINE_LINE_ROW_HEIGHT = 75

  /** Height of one row for curve. */
  const val TIMELINE_CURVE_ROW_HEIGHT = 75

  /** Offset from the top of the row to the curve. */
  const val CURVE_TOP_OFFSET = 10

  /** Offset from the bottom of the row to the curve. */
  const val CURVE_BOTTOM_OFFSET = 35

  /** Offset from the top of timeline to the first animation curve. */
  const val TIMELINE_TOP_OFFSET = 25

  /**
   * Height of the animation inspector timeline header, i.e. Transition Properties panel title and timeline labels.
   */
  const val TIMELINE_HEADER_HEIGHT = 25

  /** Vertical margin for labels.  */
  const val TIMELINE_LABEL_VERTICAL_MARGIN = 5

  /** Number of ticks per label in the timeline. */
  const val TIMELINE_TICKS_PER_LABEL = 5

  /** Label offset from the curve. */
  const val LABEL_OFFSET = 10

  val DASHED_STROKE: Stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f,
                                          floatArrayOf(JBUI.scale(3).toFloat()), 0f)

  val SIMPLE_STROKE = BasicStroke(1f)

  /** Vertical line showing the lock position. */
  val LOCK_STROKE = BasicStroke(3f)
}