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
package com.android.tools.idea.preview.animation.timeline

import com.android.tools.idea.preview.animation.InspectorColors
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.InspectorLayout.lineHalfHeightScaled
import com.android.tools.idea.preview.animation.InspectorLayout.lineHeightScaled
import com.android.tools.idea.preview.animation.InspectorLayout.outlinePaddingScaled
import com.android.tools.idea.preview.animation.InspectorLayout.timelineLineRowHeightScaled
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TimelinePanel
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * The animation line in [TimelinePanel].
 *
 * @param minX left position of the line - center of the left circle.
 * @param maxX right position of the line - center of the right circle.
 * @param rowMinY minimum y when row with animation line start.
 * @param positionProxy [PositionProxy] for the slider. @
 */
class TimelineLine(
  frozenState: SupportedAnimationManager.FrozenState,
  minX: Int,
  maxX: Int,
  rowMinY: Int,
) : TimelineElement(frozenState, minX, maxX) {

  /** Middle of the row. */
  private val middleY = rowMinY + timelineLineRowHeightScaled() / 2

  private val rectNoOffset =
    Rectangle(
      minX - lineHalfHeightScaled() - outlinePaddingScaled(),
      middleY - lineHalfHeightScaled() - outlinePaddingScaled(),
      maxX - minX + lineHeightScaled() + 2 * outlinePaddingScaled(),
      lineHeightScaled() + 2 * outlinePaddingScaled(),
    )

  override var height: Int = InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  override fun contains(x: Int, y: Int): Boolean {
    return x in rectNoOffset.x + 0..rectNoOffset.maxX.toInt() &&
      y in rectNoOffset.y..rectNoOffset.maxY.toInt()
  }

  /**
   * Painting the animation line with two circle shapes at the start and the end of the animation.
   */
  override fun paint(g: Graphics2D) {
    (g.create() as Graphics2D).apply {
      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val rect = Rectangle(rectNoOffset.x, rectNoOffset.y, rectNoOffset.width, rectNoOffset.height)
      color = InspectorColors.LINE_COLOR
      fillRoundRect(rect.x, rect.y, rect.width, rect.height, lineHeightScaled(), lineHeightScaled())
      paintCircle(this, minX, middleY)
      paintCircle(this, maxX, middleY)
    }
  }

  /**
   * Paint circle shape.
   *
   * @param x coordinate of the center of the circle
   * @param y coordinate of the center of the circle
   */
  private fun paintCircle(g: Graphics2D, x: Int, y: Int) {
    g.apply {
      color = InspectorColors.LINE_CIRCLE_OUTLINE_COLOR
      fillOval(
        x - lineHalfHeightScaled(),
        y - lineHalfHeightScaled(),
        lineHeightScaled(),
        lineHeightScaled(),
      )
      color = InspectorColors.LINE_CIRCLE_COLOR
      fillOval(
        x - lineHalfHeightScaled() + outlinePaddingScaled(),
        y - lineHalfHeightScaled() + outlinePaddingScaled(),
        lineHeightScaled() - 2 * outlinePaddingScaled(),
        lineHeightScaled() - 2 * outlinePaddingScaled(),
      )
    }
  }
}
