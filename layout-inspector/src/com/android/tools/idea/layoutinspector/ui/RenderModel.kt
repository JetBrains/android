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

import com.android.tools.idea.layoutinspector.model.DrawViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.INITIAL_ALPHA_PERCENT
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.INITIAL_LAYER_SPACING
import com.google.common.annotations.VisibleForTesting
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
import org.jetbrains.annotations.TestOnly

data class ViewDrawInfo(
  val bounds: Shape,
  val transform: AffineTransform,
  val node: DrawViewNode,
  val hitLevel: Int,
  val isCollapsed: Boolean,
)

private data class LevelListItem(val node: DrawViewNode, val isCollapsed: Boolean)

/** Class defining what is being rendered by [RenderLogic]. */
class RenderModel(
  val model: InspectorModel,
  val notificationModel: NotificationModel,
  val treeSettings: TreeSettings,
  val currentClientProvider: () -> InspectorClient,
) {
  /**
   * The last rendered level hovered over. This is different from [InspectorModel.hoveredNode],
   * since this differentiates between different layers owned by the same ViewNode.
   */
  var hoveredDrawInfo: ViewDrawInfo? = null
    set(value) {
      if (field != value) {
        field = value
        fireModified()
      }
    }

  /** The distance the image was moved in x direction to rotate the image */
  var xOff = 0.0

  /** The distance the image was moved in y direction to rotate the image */
  var yOff = 0.0

  private var visibleBounds: Rectangle = Rectangle()
  private var maxDepth: Int = 0

  @VisibleForTesting // was internal
  val maxWidth
    get() = hypot((maxDepth * layerSpacing).toFloat(), visibleBounds.width.toFloat()).toInt()

  @VisibleForTesting // was internal
  val maxHeight
    get() = hypot((maxDepth * layerSpacing).toFloat(), visibleBounds.height.toFloat()).toInt()

  val isRotated
    get() = xOff != 0.0 || yOff != 0.0

  @VisibleForTesting var hitRects = listOf<ViewDrawInfo>()

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

  private var rootBounds = Rectangle()

  init {
    model.addModificationListener { _, new, _ ->
      if (new == null) {
        overlay = null
      }
      if (!currentClientProvider().capabilities.contains(InspectorClient.Capability.SUPPORTS_SKP)) {
        resetRotation()
      }
    }
    refresh()
  }

  val isActive
    get() = !model.isEmpty

  fun selectView(x: Double, y: Double): ViewNode? {
    val view = findTopViewAt(x, y)
    model.setSelection(view, SelectionOrigin.INTERNAL)
    currentClientProvider().stats.selectionMadeFromImage(view)
    return view
  }

  fun clearSelection() {
    model.setSelection(null, SelectionOrigin.INTERNAL)
  }

  /**
   * Find all the views drawn under the given point, in order from closest to farthest from the
   * front, except if the view is an image drawn by a parent at a different depth, the depth of the
   * parent is used rather than the depth of the child.
   */
  fun findViewsAt(x: Double, y: Double): Sequence<ViewNode> =
    findDrawInfoAt(x, y).mapNotNull { it.node.findFilteredOwner(treeSettings) }.distinct()

  fun findDrawInfoAt(x: Double, y: Double): Sequence<ViewDrawInfo> =
    hitRects
      .asReversed()
      .asSequence()
      .filter { it.bounds.contains(x, y) }
      .sortedByDescending { it.hitLevel }
      .distinct()

  fun findTopViewAt(x: Double, y: Double): ViewNode? = findViewsAt(x, y).firstOrNull()

  fun rotate(xRotation: Double, yRotation: Double) {
    xOff = (xOff + xRotation).coerceIn(-1.0, 1.0)
    yOff = (yOff + yRotation).coerceIn(-1.0, 1.0)
    refresh()
  }

  fun refresh() {
    currentClientProvider().stats.currentMode3D = isRotated
    if (model.isEmpty) {
      visibleBounds = Rectangle()
      maxDepth = 0
      hitRects = emptyList()
      modificationListeners.forEach { it() }
      return
    }
    val root = model.root

    val levelLists = mutableListOf<MutableList<LevelListItem>>()
    // Each window should start completely above the previous window, hence level = levelLists.size
    ViewNode.readAccess {
      root.drawChildren.forEach { buildLevelLists(sequenceOf(it), levelLists, levelLists.size) }
    }
    maxDepth = levelLists.size

    val newHitRects = mutableListOf<ViewDrawInfo>()
    val transform = AffineTransform()
    var magnitude = 0.0
    var angle = 0.0
    if (maxDepth > 0) {
      // Only consider the bounds of visible nodes here, unlike model.root.transitiveBounds.
      ViewNode.readAccess {
        visibleBounds =
          model.root
            .flatten()
            .filter { model.isVisible(it) }
            .map { it.transitiveBounds.bounds }
            .reduceOrNull { acc, bounds -> acc.apply { add(bounds) } } ?: Rectangle()

        fun lowestVisible(node: ViewNode): Sequence<ViewNode> {
          return if (model.isVisible(node)) sequenceOf(node)
          else node.children.asSequence().flatMap { lowestVisible(it) }
        }

        rootBounds =
          model.root.children
            .flatMap { lowestVisible(it) }
            .map { it.renderBounds.bounds }
            .reduceOrNull { acc, bounds -> acc.apply { add(bounds) } } ?: Rectangle()
      }

      root.layoutBounds.x = rootBounds.x
      root.layoutBounds.y = rootBounds.y
      root.layoutBounds.width = rootBounds.width
      root.layoutBounds.height = rootBounds.height

      // Don't allow rotation to completely edge-on, since some rendering can have problems in that
      // situation. See issue 158452416.
      // You might say that this is ( •_•)>⌐■-■ / (⌐■_■) an edge-case.
      magnitude = min(0.98, hypot(xOff, yOff))
      angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

      // Transformation related to 3D view.
      transform.rotate(angle)
    } else {
      visibleBounds = Rectangle()
    }
    rebuildRectsForLevel(transform, magnitude, angle, levelLists, newHitRects)
    hitRects = newHitRects.toList()
    modificationListeners.forEach { it() }
  }

  /**
   * Figure out in what layer of the rendering the given set of sibling [nodes] should be placed.
   * The nodes will be placed in the level that is:
   * 1. At least [minLevel].
   * 2. Not overlapping with any existing nodes in [levelListCollector], except as in (4).
   * 3. If the node is non-collapsible, the same level as other sibling nodes, unless siblings
   *    themselves overlap, in which case above previous siblings.
   * 4. If the node is collapsible and the highest overlapping level is [minLevel], then at
   *    [minLevel].
   */
  private fun ViewNode.ReadAccess.buildLevelLists(
    nodes: Sequence<DrawViewNode>,
    levelListCollector: MutableList<MutableList<LevelListItem>>,
    minLevel: Int,
  ) {

    if (nodes.none()) {
      return
    }

    // Nodes from this set of siblings should be in the same level, but if they overlap or there are
    // collapsible nodes included, they may
    // not be. Collapsible nodes that aren't drawn on top of any siblings will in siblingGroups[0],
    // which is reserved for that purpose.
    // Otherwise, any nodes that overlap with others will be in the list after the latest list with
    // overlapping nodes. E.g. if you have
    // A, B, C, D, E, where B can merge with the parent, C overlaps A and D overlaps C, the result
    // would be [[B], [A, E], [C], [D]].
    val siblingGroups = mutableListOf(mutableListOf<DrawViewNode>())

    for (node in nodes) {
      // first check whether this node overlaps with any already-placed sibling nodes.
      // Starting from the highest level and going down, find the first level where something
      // intersects with this view. The target will
      // be the next level above that (that is, the last level, starting from the top, where there's
      // space).
      // For nodes already in the list we need to consider the bounds of their children as well,
      // since this node and a previous sibling
      // can't be drawn at the same level if the previous sibling's children will be drawn before
      // this node.
      val reversedIndex =
        siblingGroups.reversed().indexOfFirst {
          it.any { sibling -> sibling.intersects(node, true) }
        }
      val siblingListIndex =
        if (reversedIndex == -1) if (node.canCollapse(treeSettings)) 0 else 1
        else siblingGroups.size - reversedIndex
      siblingGroups.getOrAddSublist(siblingListIndex).add(node)
    }

    // Add the collapsible nodes first, one at a time, since they don't need to be at the same level
    // as each other.
    for (node in siblingGroups[0]) {
      if (node.findFilteredOwner(treeSettings).let { it != null && !model.isVisible(it) }) {
        // It's hidden, just recurse
        buildLevelLists(node.children(this), levelListCollector, minLevel)
        continue
      }
      val newLevelIndex =
        if (levelListCollector.isEmpty()) -1
        else
          levelListCollector.subList(minLevel, levelListCollector.size).indexOfLast { levelList ->
            levelList.any { (existing, _) -> existing.intersects(node) }
          }

      // Check if this node actually collapses into the parent
      if (
        (newLevelIndex == 0 &&
          (levelListCollector.getOrNull(minLevel)?.any {
            it.node.findFilteredOwner(treeSettings) == node.findFilteredOwner(treeSettings)
          } == true) || (newLevelIndex == -1 && node.findFilteredOwner(treeSettings) == null))
      ) {
        if (node.drawWhenCollapsed) {
          levelListCollector.getOrAddSublist(minLevel).add(LevelListItem(node, true))
        }
        buildLevelLists(node.children(this), levelListCollector, minLevel)
      } else {
        // Otherwise, add to the next available level
        levelListCollector
          .getOrAddSublist(newLevelIndex + minLevel + 1)
          .add(LevelListItem(node, false))
        buildLevelLists(node.children(this), levelListCollector, newLevelIndex + minLevel + 1)
      }
    }

    for (siblingGroup in siblingGroups.subList(1, siblingGroups.size)) {
      val filteredGroup =
        siblingGroup.filter {
          val owner = it.findFilteredOwner(treeSettings)
          owner == null || model.isVisible(owner)
        }
      // Find the lowest level that this level can sit on the existing nodes and add them there
      val newLevelIndex =
        levelListCollector.subList(minLevel, levelListCollector.size).indexOfLast { levelList ->
          levelList.any { (existing, _) -> filteredGroup.any { existing.intersects(it) } }
        } + minLevel + 1
      filteredGroup.mapTo(levelListCollector.getOrAddSublist(newLevelIndex)) {
        LevelListItem(it, false)
      }

      // recurse on each set of children (including for hidden nodes)
      for (sibling in siblingGroup) {
        val owner = sibling.findFilteredOwner(treeSettings)
        val hidden = owner != null && !model.isVisible(owner)
        buildLevelLists(
          sibling.children(this),
          levelListCollector,
          if (hidden) minLevel else newLevelIndex,
        )
      }
    }
  }

  private fun <T> MutableList<MutableList<T>>.getOrAddSublist(index: Int): MutableList<T> =
    getOrElse(index) { mutableListOf<T>().also { add(it) } }

  private fun DrawViewNode.intersects(other: DrawViewNode, useTransitiveBounds: Boolean = false) =
    (if (useTransitiveBounds) unfilteredOwner.transitiveBounds else bounds).overlap(other.bounds)

  @TestOnly fun testOverlap(shape1: Shape, shape2: Shape): Boolean = shape1.overlap(shape2)

  // Most shapes are simply Rectangles. For Rectangles use Rectangle#overlap, which is a lot  faster
  // than using Area#intersect.
  private fun Shape.overlap(other: Shape): Boolean {
    val r1 = this as? Rectangle
    val r2 = other as? Rectangle
    return when {
      r1 != null && r2 != null -> r1.overlap(r2)
      r1 != null -> other.overlap(r1)
      r2 != null -> overlap(r2)
      else -> overlap(other.bounds) && !Area(this).apply { intersect(Area(other)) }.isEmpty
    }
  }

  // Rectangle has an intersects(Rectangle) method but ad hoc tests has shown this is faster.
  private fun Rectangle.overlap(other: Rectangle): Boolean =
    x < other.x + other.width &&
      other.x < x + width &&
      y < other.y + other.height &&
      other.y < y + height

  private fun Shape.overlap(other: Rectangle): Boolean =
    this.intersects(
      other.x.toDouble(),
      other.y.toDouble(),
      other.width.toDouble(),
      other.height.toDouble(),
    )

  private fun rebuildRectsForLevel(
    transform: AffineTransform,
    magnitude: Double,
    angle: Double,
    allLevels: List<List<LevelListItem>>,
    newHitRects: MutableList<ViewDrawInfo>,
  ) {
    val ownerToLevel = mutableMapOf<ViewNode?, Int>()

    allLevels.forEachIndexed { level, levelList ->
      levelList.forEach { (view, isCollapsed) ->
        val hitLevel = ownerToLevel.getOrPut(view.findFilteredOwner(treeSettings)) { level }
        val viewTransform = AffineTransform(transform)

        val sign = if (xOff < 0) -1 else 1
        // Transformations related to 3D view.
        viewTransform.translate(magnitude * (level - maxDepth / 2) * layerSpacing * sign, 0.0)
        viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
        viewTransform.rotate(-angle)

        val rect = viewTransform.createTransformedShape(view.unfilteredOwner.renderBounds)
        newHitRects.add(ViewDrawInfo(rect, viewTransform, view, hitLevel, isCollapsed))
      }
    }
  }

  fun resetRotation() {
    if (xOff != 0.0 || yOff != 0.0) {
      xOff = 0.0
      yOff = 0.0
      refresh()
    }
  }

  /** Fire the modification listeners manually. */
  fun fireModified() = modificationListeners.forEach { it() }
}
