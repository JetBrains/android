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
import com.intellij.openapi.actionSystem.DataKey
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

val DEVICE_VIEW_MODEL_KEY = DataKey.create<DeviceViewPanelModel>(DeviceViewPanelModel::class.qualifiedName!!)

data class ViewDrawInfo(val bounds: Shape, val transform: AffineTransform, val node: ViewNode, val clip: Rectangle)

class DeviceViewPanelModel(private val model: InspectorModel) {
  @VisibleForTesting
  var xOff = 0.0
  @VisibleForTesting
  var yOff = 0.0

  private var rootBounds: Rectangle = Rectangle()
  private var maxDepth: Int = 0

  internal val maxWidth
    get() = hypot((maxDepth * layerSpacing).toFloat(), rootBounds.width.toFloat()).toInt()

  internal val maxHeight
    get() = hypot((maxDepth * layerSpacing).toFloat(), rootBounds.height.toFloat()).toInt()

  val isRotated
    get() = xOff != 0.0 || yOff != 0.0

  @VisibleForTesting
  var hitRects = listOf<ViewDrawInfo>()

  val modificationListeners = mutableListOf<() -> Unit>()

  var overlay: Image? = null
    set(value) {
      if (value != null) {
        resetRotation()
      }
      field = value
      modificationListeners.forEach { it() }
    }

  var overlayAlpha: Float = INITIAL_ALPHA_PERCENT / 100f
    set(value) {
      field = value
      modificationListeners.forEach { it() }
    }

  var layerSpacing: Int = INITIAL_LAYER_SPACING
    set(value) {
      field = value
      refresh()
    }

  init {
    model.modificationListeners.add { _, new, _ ->
      if (new == null) {
        overlay = null
      }
    }
    refresh()
  }

  val rotatable
    get() = model.hasSubImages && overlay == null

  val isActive
    get() = !model.isEmpty

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

  fun refresh() {
    if (!rotatable) {
      xOff = 0.0
      yOff = 0.0
    }
    if (model.isEmpty) {
      rootBounds = Rectangle()
      maxDepth = 0
      hitRects = emptyList()
      modificationListeners.forEach { it() }
      return
    }
    val root = model.root

    val levelLists = mutableListOf<MutableList<Pair<ViewNode, Rectangle>>>()
    // Each window should start completely above the previous window, hence level = levelLists.size
    root.children.forEach { buildLevelLists(it, root.bounds, levelLists, levelLists.size) }
    maxDepth = levelLists.size

    val newHitRects = mutableListOf<ViewDrawInfo>()
    val transform = AffineTransform()
    var magnitude = 0.0
    var angle = 0.0
    if (maxDepth > 0) {
      rootBounds = levelLists[0].map { it.second }.reduce { acc, bounds -> acc.apply { add(bounds) } }
      transform.translate(-rootBounds.width / 2.0, -rootBounds.height / 2.0)

      magnitude = min(1.0, hypot(xOff, yOff))
      angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

      transform.translate(rootBounds.width / 2.0 - rootBounds.x, rootBounds.height / 2.0 - rootBounds.y)
      transform.rotate(angle)
    }
    else {
      rootBounds = Rectangle()
    }
    rebuildRectsForLevel(transform, magnitude, angle, levelLists, newHitRects)
    hitRects = newHitRects.toList()
    modificationListeners.forEach { it() }
  }

  private fun buildLevelLists(root: ViewNode,
                              parentClip: Rectangle,
                              levelListCollector: MutableList<MutableList<Pair<ViewNode, Rectangle>>>,
                              level: Int) {
    var childClip = parentClip
    var newLevelIndex = level
    if (root.visible) {
      newLevelIndex = levelListCollector
        .subList(level, levelListCollector.size)
        .indexOfFirst { it.none { (node, _) -> node.bounds.intersects(root.bounds) } }
      if (newLevelIndex == -1) {
        newLevelIndex = levelListCollector.size
        levelListCollector.add(mutableListOf())
      }
      else {
        newLevelIndex += level
      }
      val levelList = levelListCollector[newLevelIndex]
      childClip = parentClip.intersection(root.bounds)
      levelList.add(Pair(root, childClip))
    }
    root.children.forEach { buildLevelLists(it, childClip, levelListCollector, newLevelIndex) }
  }

  private fun rebuildRectsForLevel(transform: AffineTransform,
                                   magnitude: Double,
                                   angle: Double,
                                   allLevels: List<List<Pair<ViewNode, Rectangle>>>,
                                   newHitRects: MutableList<ViewDrawInfo>) {
    allLevels.forEachIndexed { level, levelList ->
      levelList.forEach { (view, clip) ->
        val viewTransform = AffineTransform(transform)

        val sign = if (xOff < 0) -1 else 1
        viewTransform.translate(magnitude * (level - maxDepth / 2) * layerSpacing * sign, 0.0)
        viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
        viewTransform.rotate(-angle)
        viewTransform.translate(-rootBounds.width / 2.0, -rootBounds.height / 2.0)

        val rect = viewTransform.createTransformedShape(view.bounds)
        newHitRects.add(ViewDrawInfo(rect, viewTransform, view, clip))
      }
    }
  }

  fun resetRotation() {
    xOff = 0.0
    yOff = 0.0
    refresh()
  }
}