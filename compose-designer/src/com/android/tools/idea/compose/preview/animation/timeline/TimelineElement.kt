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

import com.android.tools.idea.compose.preview.animation.TooltipInfo
import com.android.tools.idea.res.clamp
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
  Dragged
}

/** Group of [TimelineElement] for timeline. Group elements are moved and frozen together. */
open class ParentTimelineElement(
  state: ElementState,
  private val children: List<TimelineElement>,
  positionProxy: PositionProxy
) :
  TimelineElement(
    state = state,
    minX = children.minOf { it.minX },
    maxX = children.maxOf { it.maxX },
    positionProxy = positionProxy
  ) {
  override var height = children.sumOf { it.height }
  override fun contains(x: Int, y: Int) = children.any { it.contains(x, y) }
  override fun paint(g: Graphics2D) {
    children.forEach { it.paint(g) }
  }

  override fun moveComponents(actualDeltaPx: Int) {
    children.forEach {
      it.offsetPx = offsetPx
      it.moveComponents(actualDeltaPx)
    }
  }

  override fun getTooltip(point: Point): TooltipInfo? {
    return children.firstNotNullOfOrNull { it.getTooltip(point) }
  }

  override fun reset() {
    super.reset()
    children.forEach { it.reset() }
  }

  override var status: TimelineElementStatus = TimelineElementStatus.Inactive
    set(value) {
      field = value
      children.forEach { it.status = value }
    }
}

/** Drawable element for timeline. Each element could be moved and frozen. */
abstract class TimelineElement(
  val state: ElementState,
  val minX: Int,
  val maxX: Int,
  protected val positionProxy: PositionProxy
) : Disposable {

  var offsetPx: Int = 0
  abstract var height: Int
  fun heightScaled(): Int = JBUI.scale(height)

  open var frozen: Boolean
    get() = state.frozen
    set(value) {
      state.frozen = value
    }

  open fun getTooltip(point: Point): TooltipInfo? = null

  open var status: TimelineElementStatus = TimelineElementStatus.Inactive

  init {
    offsetPx =
      if (state.valueOffset > 0)
        positionProxy.xPositionForValue(positionProxy.minimumValue() + state.valueOffset) -
          positionProxy.minimumXPosition()
      else
        -positionProxy.xPositionForValue(positionProxy.minimumValue() - state.valueOffset) +
          positionProxy.minimumXPosition()
  }

  abstract fun contains(x: Int, y: Int): Boolean
  abstract fun paint(g: Graphics2D)

  fun move(deltaPx: Int) {
    val previousOffsetPx = offsetPx
    offsetPx =
      clamp(
        offsetPx + deltaPx,
        positionProxy.minimumXPosition() - maxX,
        positionProxy.maximumXPosition() - minX
      )
    state.valueOffset =
      if (offsetPx >= 0)
        positionProxy.valueForXPosition(minX + offsetPx) - positionProxy.valueForXPosition(minX)
      else positionProxy.valueForXPosition(maxX + offsetPx) - positionProxy.valueForXPosition(maxX)
    moveComponents(actualDeltaPx = offsetPx - previousOffsetPx)
  }

  open fun moveComponents(actualDeltaPx: Int) {}

  open fun reset() {
    offsetPx = 0
    state.valueOffset = 0
  }

  fun contains(point: Point): Boolean {
    return contains(point.x, point.y)
  }

  override fun dispose() {}
}
