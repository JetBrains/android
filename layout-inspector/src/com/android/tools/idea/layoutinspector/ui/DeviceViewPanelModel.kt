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

import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.BITMAP_AS_REQUESTED
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.UNKNOWN
 import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.DrawViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataKey
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

val DEVICE_VIEW_MODEL_KEY = DataKey.create<DeviceViewPanelModel>(DeviceViewPanelModel::class.qualifiedName!!)

data class ViewDrawInfo(
  val bounds: Shape,
  val transform: AffineTransform,
  val node: DrawViewNode,
  val hitLevel: Int,
  val isCollapsed: Boolean
)

private data class LevelListItem(val node: DrawViewNode, val isCollapsed: Boolean)

class DeviceViewPanelModel(private val model: InspectorModel, private val client: (() -> InspectorClient?)? = null) {
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
      if (client?.invoke()?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) != true) {
        resetRotation()
      }
    }
    refresh()
  }

  val pictureType
    get() =
      when {
        model.windows.values.any { it.imageType == BITMAP_AS_REQUESTED } -> {
          // If we find that we've requested and received a png, that's what we'll use first
          BITMAP_AS_REQUESTED
        }
        else -> {
          UNKNOWN
        }
      }

  val isActive
    get() = !model.isEmpty

  /**
   * Find all the views drawn under the given point, in order from closest to farthest from the front, except
   * if the view is an image drawn by a parent at a different depth, the depth of the parent is used rather than
   * the depth of the child.
   */
  fun findViewsAt(x: Double, y: Double): Sequence<ViewNode> =
    hitRects.asReversed()
      .asSequence()
      .filter { it.bounds.contains(x, y) }
      .sortedByDescending { it.hitLevel }
      .map { it.node.owner }
      .distinct()

  fun findTopViewAt(x: Double, y: Double): ViewNode? = findViewsAt(x, y).firstOrNull()

  fun rotate(xRotation: Double, yRotation: Double) {
    xOff = (xOff + xRotation).coerceIn(-1.0, 1.0)
    yOff = (yOff + yRotation).coerceIn(-1.0, 1.0)
    refresh()
  }

  fun refresh() {
    if (xOff == 0.0 && yOff == 0.0) {
      model.stats.rotation.toggledTo2D()
    }
    else {
      model.stats.rotation.toggledTo3D()
    }
    if (model.isEmpty) {
      rootBounds = Rectangle()
      maxDepth = 0
      hitRects = emptyList()
      modificationListeners.forEach { it() }
      return
    }
    val root = model.root

    val levelLists = mutableListOf<MutableList<LevelListItem>>()
    // Each window should start completely above the previous window, hence level = levelLists.size
    ViewNode.readDrawChildren { drawChildren ->
      root.drawChildren().forEach { buildLevelLists(it, levelLists, levelLists.size, drawChildren) }
    }
    maxDepth = levelLists.size

    val newHitRects = mutableListOf<ViewDrawInfo>()
    val transform = AffineTransform()
    var magnitude = 0.0
    var angle = 0.0
    if (maxDepth > 0) {
      rootBounds = levelLists[0].map { it.node.owner.transformedBounds.bounds }.reduce { acc, bounds -> acc.apply { add(bounds) } }
      root.x = rootBounds.x
      root.y = rootBounds.x
      root.width = rootBounds.width
      root.height = rootBounds.height
      transform.translate(-rootBounds.width / 2.0, -rootBounds.height / 2.0)

      // Don't allow rotation to completely edge-on, since some rendering can have problems in that situation. See issue 158452416.
      // You might say that this is ( •_•)>⌐■-■ / (⌐■_■) an edge-case.
      magnitude = min(0.98, hypot(xOff, yOff))
      angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

      transform.translate(rootBounds.width / 2.0 - rootBounds.x, rootBounds.height / 2.0 - rootBounds.y)
      transform.rotate(angle)
    }
    else {
      rootBounds = Rectangle()
    }
    rebuildRectsForLevel(transform, magnitude, angle, levelLists, newHitRects)
    newHitRects.forEach { it.bounds }
    hitRects = newHitRects.toList()
    modificationListeners.forEach { it() }
  }

  private fun buildLevelLists(root: DrawViewNode,
                              levelListCollector: MutableList<MutableList<LevelListItem>>,
                              minLevel: Int,
                              drawChildren: ViewNode.() -> List<DrawViewNode>) {
    var newLevelIndex = levelListCollector.size
    if (root.owner.visible) {
      // Starting from the highest level and going down, find the first level where something intersects with this view. We'll put this view
      // in the next level above that (that is, the last level, starting from the top, where there's space).
      val rootArea = Area(root.owner.transformedBounds)
      newLevelIndex = levelListCollector
        .subList(minLevel, levelListCollector.size)
        .indexOfLast { it.map { (node, _) -> Area(node.owner.transformedBounds) }.any { a -> a.run { intersect(rootArea); !isEmpty } } }
      if (newLevelIndex == -1) {
        newLevelIndex = levelListCollector.size
      }
      else {
        newLevelIndex += minLevel + 1
      }
      val levelList = levelListCollector.getOrElse(newLevelIndex) {
        mutableListOf<LevelListItem>().also { levelListCollector.add(it) }
      }
      levelList.add(LevelListItem(root, false))
      if (!root.canCollapse) {
        // Add leading images to this level
        root.owner.drawChildren()
          .takeWhile { it.canCollapse }
          .mapTo(levelList) { LevelListItem(it, true) }
      }
    }
    if (root is DrawViewChild) {
      var sawChild = false
      for (drawChild in root.owner.drawChildren()) {
        if (!sawChild && drawChild is DrawViewImage) {
          // Skip leading images -- they're already added
          continue
        }
        sawChild = true
        buildLevelLists(drawChild, levelListCollector, newLevelIndex, drawChildren)
      }
    }
  }

  private fun rebuildRectsForLevel(transform: AffineTransform,
                                   magnitude: Double,
                                   angle: Double,
                                   allLevels: List<List<LevelListItem>>,
                                   newHitRects: MutableList<ViewDrawInfo>) {
    val ownerToLevel = mutableMapOf<ViewNode, Int>()

    allLevels.forEachIndexed { level, levelList ->
      levelList.forEach { (view, isCollapsed) ->
        val hitLevel = ownerToLevel.getOrPut(view.owner) { level }
        val viewTransform = AffineTransform(transform)

        val sign = if (xOff < 0) -1 else 1
        viewTransform.translate(magnitude * (level - maxDepth / 2) * layerSpacing * sign, 0.0)
        viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
        viewTransform.rotate(-angle)
        viewTransform.translate(-rootBounds.width / 2.0, -rootBounds.height / 2.0)

        val rect = viewTransform.createTransformedShape(view.owner.transformedBounds)
        newHitRects.add(ViewDrawInfo(rect, viewTransform, view, hitLevel, isCollapsed))
      }
    }
  }

  fun resetRotation() {
    xOff = 0.0
    yOff = 0.0
    refresh()
  }
}