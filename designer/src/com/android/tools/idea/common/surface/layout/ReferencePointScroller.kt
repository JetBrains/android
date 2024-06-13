/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.common.surface.layout

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.SceneView
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * When the view size is changed, use the reference point as the anchor to keep the new scroll
 * position at a distance to the newReferencePoint that is equal to the distance the
 * [oldScrollPosition] had to the [oldReferencePoint].
 */
open class ReferencePointScroller(
  @SwingCoordinate private val oldViewSize: Dimension,
  @SwingCoordinate private val oldScrollPosition: Point,
  @SwingCoordinate private val oldReferencePoint: Point,
  oldScale: Double,
  newScale: Double,
  private val oldRectangles: Map<SceneView, Rectangle?> = emptyMap(),
  private val newRectangleProvider: (SceneView) -> Rectangle? = { null },
) : DesignSurfaceViewportScroller {
  private val scaleChange = newScale / maxOf(oldScale, 1e-6)

  override fun scroll(port: DesignSurfaceViewport) {
    // the preferred size would be the actual canvas size in viewport.
    val newViewSize = port.viewComponent.preferredSize
    if (newViewSize.width == 0 || newViewSize.height == 0) {
      return
    }
    if (oldViewSize.width == 0 || oldViewSize.height == 0) {
      // The view was not visible before, no need to change the scroll position.
      return
    }
    val portSize = port.extentSize
    val newReferencePoint = expectedPositionAfterZoom(oldReferencePoint.x, oldReferencePoint.y)
    val newViewPositionX =
      if (portSize.width >= newViewSize.width) 0
      else {
        getNewScrollPosition(
          oldScrollPosition.x,
          oldReferencePoint.x,
          newReferencePoint.x,
          portSize.width,
        )
      }
    val newViewPositionY =
      if (portSize.height >= newViewSize.height) 0
      else {
        getNewScrollPosition(
          oldScrollPosition.y,
          oldReferencePoint.y,
          newReferencePoint.y,
          portSize.height,
        )
      }
    port.viewPosition = Point(newViewPositionX, newViewPositionY)
  }

  /**
   * Calculates the new scroll position for one dimension by trying to keep the same distance with
   * its reference.
   */
  private fun getNewScrollPosition(
    oldScrollPosition: Int,
    oldReference: Int,
    newReference: Int,
    maxDistance: Int,
  ): Int {
    val distance = (oldReference - oldScrollPosition).coerceIn(0, maxDistance)
    return (newReference - distance).coerceAtLeast(0)
  }

  /**
   * The new position in theory should be (x * scaleChange, y * scaleChange) if the content is
   * scaled by [scaleChange], but this is not the case as some components have minimum sizes and all
   * of their dimensions also get rounded to integer values. To avoid having a big accumulated error
   * on low zoom levels or when the viewport contains many components, the position is treated as
   * relative to its closest [SceneView].
   */
  private fun expectedPositionAfterZoom(x: Int, y: Int): Point {
    var closestSceneView: SceneView? = null
    var bestDistance = -1
    for ((sceneView, rect) in oldRectangles) {
      if (rect == null) continue
      // use manhattan distance for simplicity
      val currentDistance =
        (rect.x - x).coerceAtLeast(0) +
          (x - (rect.x + rect.width)).coerceAtLeast(0) +
          (rect.y - y).coerceAtLeast(0) +
          (y - (rect.y + rect.height)).coerceAtLeast(0)
      if (closestSceneView == null || currentDistance < bestDistance) {
        bestDistance = currentDistance
        closestSceneView = sceneView
      }
    }
    if (closestSceneView == null) {
      return Point((x * scaleChange).toInt(), (y * scaleChange).toInt())
    }
    val oldRectangle = oldRectangles[closestSceneView]
    val newRectangle = newRectangleProvider(closestSceneView)
    if (oldRectangle == null || newRectangle == null) {
      return Point((x * scaleChange).toInt(), (y * scaleChange).toInt())
    }
    // Here is the key to avoid a big error, the "theoretical" scale change is
    // only applied to the distance between (x,y) and its closest scene view,
    // instead of applying it to its absolute position.
    val dx = ((x - oldRectangle.x) * scaleChange).toInt()
    val dy = ((y - oldRectangle.y) * scaleChange).toInt()
    return Point(newRectangle.x + dx, newRectangle.y + dy)
  }
}
