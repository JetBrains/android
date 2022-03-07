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

import com.android.tools.idea.compose.preview.animation.InspectorColors
import com.android.tools.idea.compose.preview.animation.InspectorLayout
import com.android.tools.idea.compose.preview.animation.InspectorLayout.OUTLINE_PADDING
import com.android.tools.idea.compose.preview.animation.TimelinePanel
import com.android.tools.idea.compose.preview.animation.Transition
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints


/**
 * The animation line in [TimelinePanel].
 * @param minX left position of the line - center of the left circle.
 * @param maxX right position of the the line - center of the right circle.
 * @param rowMinY minimum y when row with animation line start.
 * @param positionProxy [PositionProxy] for the slider.
 * @
 */
class TimelineLine(state: ElementState, minX: Int, maxX: Int, rowMinY: Int, positionProxy: PositionProxy) :
  TimelineElement(state, minX, maxX, positionProxy) {

  private val minY = rowMinY + (InspectorLayout.TIMELINE_LINE_ROW_HEIGHT - InspectorLayout.LINE_HEIGHT) / 2

  constructor(state: ElementState, transition: Transition, maxY: Int, positionProxy: PositionProxy)
    : this(state, transition.startMillis?.let { positionProxy.xPositionForValue(it) } ?: (positionProxy.minimumXPosition()),
           transition.endMillis?.let { positionProxy.xPositionForValue(it) } ?: positionProxy.maximumXPosition(),
           maxY, positionProxy)


  private val rectNoOffset = Rectangle(
    minX - InspectorLayout.LINE_HALF_HEIGHT - OUTLINE_PADDING,
    minY - InspectorLayout.LINE_HALF_HEIGHT - OUTLINE_PADDING,
    maxX - minX + InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING,
    InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING)

  override var height: Int = InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  override fun contains(x: Int, y: Int): Boolean {
    return x in rectNoOffset.x + offsetPx..rectNoOffset.maxX.toInt() + offsetPx &&
           y in rectNoOffset.y..rectNoOffset.maxY.toInt()
  }

  /**
   * Painting the animation line with two circle shapes at the start and the end of the animation.
   */
  override fun paint(g: Graphics2D) {
    (g.create() as Graphics2D).apply {
      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      if (offsetPx != 0) {
        color = InspectorColors.LINE_COLOR
        stroke = InspectorLayout.DASHED_STROKE
        drawRoundRect(rectNoOffset.x, rectNoOffset.y + OUTLINE_PADDING,
                      rectNoOffset.width, rectNoOffset.height - 2 * OUTLINE_PADDING,
                      InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING,
                      InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING)
        stroke = InspectorLayout.SIMPLE_STROKE
      }
      val yOffset = if (status == TimelineElementStatus.Dragged) -2 else 0
      val rect = Rectangle(
        rectNoOffset.x + offsetPx,
        rectNoOffset.y + yOffset,
        rectNoOffset.width,
        rectNoOffset.height)
      if (status == TimelineElementStatus.Dragged || status == TimelineElementStatus.Hovered) {
        color = InspectorColors.LINE_OUTLINE_COLOR_ACTIVE
        drawRoundRect(rect.x - OUTLINE_PADDING, rect.y - OUTLINE_PADDING, rect.width + 2 * OUTLINE_PADDING,
                      rect.height + 2 * OUTLINE_PADDING,
                      InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING, InspectorLayout.LINE_HEIGHT + 2 * OUTLINE_PADDING)
      }
      color = InspectorColors.LINE_COLOR
      fillRoundRect(rect.x, rect.y, rect.width, rect.height,
                    InspectorLayout.LINE_HEIGHT, InspectorLayout.LINE_HEIGHT)
      paintCircle(this, minX + offsetPx, minY + yOffset)
      paintCircle(this, maxX + offsetPx, minY + yOffset)
    }
  }

  /**
   * Paint circle shape.
   * @param x coordinate of the center of the circle
   * @param y coordinate of the center of the circle
   */
  private fun paintCircle(g: Graphics2D, x: Int, y: Int) {
    g.apply {
      color = InspectorColors.LINE_CIRCLE_OUTLINE_COLOR
      fillOval(x - InspectorLayout.LINE_HALF_HEIGHT,
               y - InspectorLayout.LINE_HALF_HEIGHT,
               InspectorLayout.LINE_HEIGHT, InspectorLayout.LINE_HEIGHT)
      color = InspectorColors.LINE_CIRCLE_COLOR
      fillOval(x - InspectorLayout.LINE_HALF_HEIGHT + OUTLINE_PADDING, y - InspectorLayout.LINE_HALF_HEIGHT + OUTLINE_PADDING,
               InspectorLayout.LINE_HEIGHT - 2 * OUTLINE_PADDING, InspectorLayout.LINE_HEIGHT - 2 * OUTLINE_PADDING)
    }
  }
}