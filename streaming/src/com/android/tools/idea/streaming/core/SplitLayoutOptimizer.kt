/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.util.scaled
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.ui.content.ContentManager
import java.awt.Component
import java.awt.Container
import java.awt.Point
import kotlin.math.min

internal class SimplePairLayout(override val side: Int, override val splitRatio: Float) : PairLayout

/** Computes and returns the optimal split layout for two devices, or null if the optimal layout cannot be computed. */
internal fun computeOptimalSplitLayout(
  contentManager: ContentManager,
  panel1: AbstractDevicePanel<*>,
  panel2: AbstractDevicePanel<*>,
): PairLayout? {
  val displayView1 = panel1.primaryDisplayView ?: return null
  val displayView2 = panel2.primaryDisplayView ?: return null
  val p1 = displayView1.locationRelativeToContainer(panel1) ?: return null
  val p2 = displayView2.locationRelativeToContainer(panel2) ?: return null
  val selectedComponent = contentManager.component
  val decorator = selectedComponent.findAncestor<InternalDecorator>() ?: return null
  if (decorator.width == 0 || decorator.height == 0) {
    return null
  }
  val contentSize1 = displayView1.naturalContentSize.scaled(1 / displayView1.screenScalingFactor)
  val contentSize2 = displayView2.naturalContentSize.scaled(1 / displayView2.screenScalingFactor)
  val marginWidth = decorator.width - selectedComponent.width
  val availableWidth = decorator.width - 2 * marginWidth - p1.x - p2.x - DIVIDER_WIDTH
  val scaleWidth = availableWidth.toFloat() / (contentSize1.width + contentSize2.width)
  val marginHeight = decorator.height - selectedComponent.height
  val availableHeight = decorator.height - 2 * marginHeight - p1.y - p2.y - DIVIDER_WIDTH
  val scaleHeight = availableHeight.toFloat() / (contentSize1.height + contentSize2.height)
  if (scaleWidth >= scaleHeight) {
    // Horizontal layout.
    val maxUsable1 = min(contentSize1.width, (selectedComponent.height - p1.y).scaledDown(contentSize1.width, contentSize1.height))
    val maxUsable2 = min(contentSize2.width, (selectedComponent.height - p2.y).scaledDown(contentSize2.width, contentSize2.height))
    val r =
      when {
        // Distribute available horizontal space proportional to usable size.
        maxUsable1 + maxUsable2 <= availableWidth -> maxUsable1.toFloat() / (maxUsable1 + maxUsable2)
        // Distribute available horizontal space proportional to content size but not exceeding the usable size.
        maxUsable1 < contentSize1.width * scaleWidth -> maxUsable1.toFloat() / availableWidth
        maxUsable2 < contentSize2.width * scaleWidth -> 1 - maxUsable2.toFloat() / availableWidth
        else -> contentSize1.width.toFloat() / (contentSize1.width + contentSize2.width)
      }
    val splitRatio = (availableWidth * r + p1.x + marginWidth) / decorator.width
    return SimplePairLayout(PairLayout.RIGHT, splitRatio)
  } else {
    // Vertical layout.
    val maxUsable1 = min(contentSize1.height, (selectedComponent.width - p1.x).scaledDown(contentSize1.height, contentSize1.width))
    val maxUsable2 = min(contentSize2.height, (selectedComponent.width - p2.x).scaledDown(contentSize2.height, contentSize2.width))
    val r =
      when {
        // Distribute available vertical space proportional to usable size.
        maxUsable1 + maxUsable2 <= availableHeight -> maxUsable1.toFloat() / (maxUsable1 + maxUsable2)
        // Distribute available vertical space proportional to content size but not exceeding the usable size.
        maxUsable1 < contentSize1.height * scaleHeight -> maxUsable1.toFloat() / availableHeight
        maxUsable2 < contentSize2.height * scaleHeight -> 1 - maxUsable2.toFloat() / availableHeight
        else -> contentSize1.height.toFloat() / (contentSize1.height + contentSize2.height)
      }
    val splitRatio = (availableHeight * r + p1.y + marginHeight) / decorator.height
    return SimplePairLayout(PairLayout.BOTTOM, splitRatio)
  }
}

private fun Component.locationRelativeToContainer(container: Container): Point? {
  val point = Point()
  var c = this
  while (c != container) {
    point.translate(c.x, c.y)
    c = c.parent ?: return null
  }
  return point
}

private const val DIVIDER_WIDTH = 1
