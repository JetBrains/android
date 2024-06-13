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

import com.android.tools.idea.preview.animation.TooltipInfo
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Point

/** Proxy to slider positions. */
interface PositionProxy {
  fun xPositionForValue(value: Int): Int

  fun valueForXPosition(value: Int): Int

  fun minimumXPosition(): Int

  fun maximumXPosition(): Int

  fun maximumValue(): Int

  fun minimumValue(): Int
}

/** Status of [TimelineElement] in timeline. */
enum class TimelineElementStatus {
  Inactive,
  Hovered,
  Dragged,
}

/** Group of [TimelineElement] for timeline. Group elements are moved and frozen together. */
open class ParentTimelineElement(
  valueOffset: Int,
  frozenValue: Int?,
  private val children: List<TimelineElement>,
) :
  TimelineElement(
    offsetPx = valueOffset,
    frozenValue,
    minX = children.minOfOrNull { it.minX } ?: 0,
    maxX = children.maxOfOrNull { it.maxX } ?: 0,
  ) {

  override val height = children.sumOf { it.height }

  override fun contains(x: Int, y: Int) = children.any { it.contains(x, y) }

  override fun paint(g: Graphics2D) {
    children.forEach { it.paint(g) }
  }

  override fun getTooltip(point: Point): TooltipInfo? {
    return children.firstNotNullOfOrNull { it.getTooltip(point) }
  }

  override var status: TimelineElementStatus = TimelineElementStatus.Inactive
    set(value) {
      field = value
      children.forEach { it.status = value }
    }
}

/** Drawable element for timeline. Each element could be moved and frozen. */
abstract class TimelineElement(
  val offsetPx: Int,
  val frozenValue: Int?,
  val minX: Int,
  val maxX: Int,
) : Disposable {

  abstract val height: Int

  fun heightScaled(): Int = JBUI.scale(height)

  open fun getTooltip(point: Point): TooltipInfo? = null

  open var status: TimelineElementStatus = TimelineElementStatus.Inactive

  abstract fun contains(x: Int, y: Int): Boolean

  abstract fun paint(g: Graphics2D)

  fun setNewOffset(deltaPx: Int) {
    newOffsetCallback(deltaPx)
  }

  fun contains(point: Point): Boolean {
    return contains(point.x, point.y)
  }

  override fun dispose() {}

  private var newOffsetCallback: (Int) -> Unit = {}

  /**
   * Sets the callback invoked when this [TimelineElement] is moved(dragged on timeline) and have a
   * new offset. The callback receives a new offset in pixels (`offsetPx`).
   *
   * @param newOffsetCallback The function to call on element finished dragging.
   */
  fun setNewOffsetCallback(newOffsetCallback: (Int) -> Unit) {
    this.newOffsetCallback = newOffsetCallback
  }
}

fun getOffsetForValue(valueOffset: Int, positionProxy: PositionProxy) =
  if (valueOffset > 0)
    positionProxy.xPositionForValue(positionProxy.minimumValue() + valueOffset) -
      positionProxy.minimumXPosition()
  else
    -positionProxy.xPositionForValue(positionProxy.minimumValue() - valueOffset) +
      positionProxy.minimumXPosition()
