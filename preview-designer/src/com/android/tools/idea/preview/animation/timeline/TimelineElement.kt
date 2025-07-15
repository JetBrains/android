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

import com.android.tools.idea.preview.animation.SupportedAnimationManager
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

/** Group of [TimelineElement] for timeline. Group elements are moved and frozen together. */
open class ParentTimelineElement(
  frozenState: SupportedAnimationManager.FrozenState,
  private val children: List<TimelineElement>,
) :
  TimelineElement(
    frozenState,
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
}

/** Drawable element for timeline. Each element could be moved and frozen. */
abstract class TimelineElement(
  val frozenState: SupportedAnimationManager.FrozenState,
  val minX: Int,
  val maxX: Int,
) : Disposable {

  abstract val height: Int

  fun heightScaled(): Int = JBUI.scale(height)

  open fun getTooltip(point: Point): TooltipInfo? = null

  abstract fun contains(x: Int, y: Int): Boolean

  abstract fun paint(g: Graphics2D)

  fun contains(point: Point): Boolean {
    return contains(point.x, point.y)
  }

  override fun dispose() {}
}
