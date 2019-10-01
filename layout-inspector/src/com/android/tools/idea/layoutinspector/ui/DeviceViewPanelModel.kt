/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

private const val LAYER_SPACING = 150

data class ViewDrawInfo(val bounds: Shape, val transform: AffineTransform, val node: ViewNode, val clip: Rectangle)

class DeviceViewPanelModel(private val model: InspectorModel) {
  @VisibleForTesting
  var xOff = 0.0
  @VisibleForTesting
  var yOff = 0.0

  private var rootDimension: Dimension = Dimension()
  private var maxDepth: Int = 0

  internal val maxWidth
    get() = hypot((maxDepth * LAYER_SPACING).toFloat(), rootDimension.width.toFloat()).toInt()

  internal val maxHeight
    get() = hypot((maxDepth * LAYER_SPACING).toFloat(), rootDimension.height.toFloat()).toInt()

  @VisibleForTesting
  var hitRects = listOf<ViewDrawInfo>()

  init {
    refresh()
  }

  fun findTopRect(x: Double, y: Double): ViewNode? {
    return hitRects.findLast {
      it.bounds.contains(x, y)
    }?.node
  }

  fun rotate(xRotation: Double, yRotation: Double) {
    xOff = (xOff + xRotation).coerceIn(-1.0, 1.0)
    yOff = (yOff + yRotation).coerceIn(-1.0, 1.0)
    refresh()
  }

  @VisibleForTesting
  fun refresh() {
    val root = model.root
    if (root == null) {
      rootDimension = Dimension(0, 0)
      maxDepth = 0
      hitRects = emptyList()
      return
    }
    rootDimension = Dimension(root.width, root.height)
    val newHitRects = mutableListOf<ViewDrawInfo>()
    val transform = AffineTransform()
    transform.translate(-root.width / 2.0, -root.height / 2.0)

    val magnitude = min(1.0, hypot(xOff, yOff))
    val angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

    transform.translate(rootDimension.width / 2.0 - root.x, rootDimension.height / 2.0 - root.y)
    transform.rotate(angle)
    val levelLists = mutableListOf<MutableList<MutableList<Pair<ViewNode, Rectangle>>>>()
    buildLevelLists(root, root.bounds, levelLists)

    rebuildRectsForLevel(transform, magnitude, angle, levelLists, newHitRects)
    maxDepth = levelLists.size
    hitRects = newHitRects.toList()
  }

  private fun buildLevelLists(root: ViewNode,
                              parentClip: Rectangle,
                              levelListCollector: MutableList<MutableList<MutableList<Pair<ViewNode, Rectangle>>>>,
                              level: Int = 0) {
    val levelList = levelListCollector.getOrNull(level)
                    ?: mutableListOf<MutableList<Pair<ViewNode, Rectangle>>>().also { levelListCollector.add(it) }
    val clip = parentClip.intersection(root.bounds)
    // add to the first sub level list with no rects that intersect ours
    val subLevelList = levelList.find { it.none { (node, _) -> node.bounds.intersects(root.bounds) } }
                       ?: mutableListOf<Pair<ViewNode, Rectangle>>().also { levelList.add(it) }
    subLevelList.add(Pair(root, clip))
    root.children.forEach { buildLevelLists(it, clip, levelListCollector, level + 1) }
  }

  private fun rebuildRectsForLevel(transform: AffineTransform,
                                   magnitude: Double,
                                   angle: Double,
                                   allLevels: List<List<List<Pair<ViewNode, Rectangle>>>>,
                                   newHitRects: MutableList<ViewDrawInfo>) {
    allLevels.forEachIndexed { level, levelList ->
      levelList.forEachIndexed { subLevel, subLevelList ->
        subLevelList.forEach { (view, clip) ->
          val viewTransform = AffineTransform(transform)

          val sign = if (xOff < 0) -1 else 1
          viewTransform.translate(
            magnitude * ((level - maxDepth / 2) + (subLevel.toFloat() / levelList.size.toFloat())) * LAYER_SPACING * sign,
            0.0)
          viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
          viewTransform.rotate(-angle)
          viewTransform.translate(-rootDimension.width / 2.0, -rootDimension.height / 2.0)

          val rect = viewTransform.createTransformedShape(view.bounds)
          newHitRects.add(ViewDrawInfo(rect, viewTransform, view, clip))
        }
      }
    }
  }

  fun resetRotation() {
    xOff = 0.0
    yOff = 0.0
    refresh()
  }
}