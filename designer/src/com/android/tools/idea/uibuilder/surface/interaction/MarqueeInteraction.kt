/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.interaction

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.draw.ColorSet
import com.android.tools.idea.common.scene.draw.drawLasso
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionEvent
import com.android.tools.idea.common.surface.InteractionInformation
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.MouseDraggedEvent
import com.android.tools.idea.common.surface.SceneView
import com.intellij.util.containers.ContainerUtil
import java.awt.Cursor
import java.awt.Graphics2D
import kotlin.math.abs

/**
 * A [MarqueeInteraction] is an interaction for swiping out a selection rectangle. With a modifier
 * key, items that intersect the rectangle can be toggled instead of added to the new selection set.
 */
class MarqueeInteraction(private val sceneView: SceneView, private val repaint: () -> Unit) :
  Interaction {
  /** The [Layer] drawn for the marquee. */
  private var overlay: MarqueeLayer? = null
  private var startInfo: InteractionInformation? = null

  override fun begin(event: InteractionEvent) {
    assert(event is MouseDraggedEvent) {
      "The instance of event should be MouseDraggedEvent but it is ${event.javaClass}; The SceneView is $sceneView, start (x, y) = ${event.info.x}, ${event.info.y}, start mask is ${event.info.modifiersEx}"
    }
    startInfo = event.info
  }

  override fun update(event: InteractionEvent) {
    if (event is MouseDraggedEvent) {
      val mouseEvent = event.eventObject
      if (overlay == null || startInfo == null) {
        return
      }
      val x = mouseEvent.x
      val y = mouseEvent.y
      val startX = startInfo!!.x
      val startY = startInfo!!.y
      val xp = x.coerceAtMost(startX)
      val yp = y.coerceAtMost(startY)
      val w = abs(x - startX)
      val h = abs(y - startY) // Convert to Android coordinates and compute selection overlaps
      val ax = Coordinates.getAndroidXDip(sceneView, xp)
      val ay = Coordinates.getAndroidYDip(sceneView, yp)
      val aw = Coordinates.getAndroidDimensionDip(sceneView, w)
      val ah = Coordinates.getAndroidDimensionDip(sceneView, h)
      overlay!!.updateValues(xp, yp, w, h, x, y, aw, ah)
      val within: Collection<SceneComponent> = sceneView.scene.findWithin(ax, ay, aw, ah)
      val result = ContainerUtil.map(within) { it.nlComponent }
      sceneView.selectionModel.setSelection(result)
      repaint()
    }
  }

  override fun commit(event: InteractionEvent) { // Do nothing
  }

  override fun cancel(
    event: InteractionEvent
  ) { //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    sceneView.selectionModel.clear()
  }

  override fun getCursor(): Cursor? {
    return null
  }

  override fun createOverlays(): List<Layer> {
    val colorSet = sceneView.colorSet
    overlay = MarqueeLayer(colorSet)
    return listOf<Layer>(overlay!!)
  }

  /**
   * An [Layer] for the [MarqueeInteraction]; paints a selection overlay rectangle matching the
   * mouse coordinate delta between gesture start and the current position.
   */
  private class MarqueeLayer(val colorSet: ColorSet) : Layer() {
    @SwingCoordinate private var x = 0

    @SwingCoordinate private var y = 0

    @SwingCoordinate private var w = 0

    @SwingCoordinate private var h = 0

    @SwingCoordinate private var mouseX = 0

    @SwingCoordinate private var mouseY = 0

    @AndroidDpCoordinate private var androidWidth = 0

    @AndroidDpCoordinate private var androidHeight = 0

    /**
     * Updates the attribute of the marquee rectangle.
     *
     * @param x The top left corner of the rectangle, x coordinate. The unit is swing (pixel)
     * @param y The top left corner of the rectangle, y coordinate. The unit is swing (pixel)
     * @param w Rectangle w. The unit is swing (pixel)
     * @param h Rectangle h. The unit is swing (pixel)
     * @param mouseX The x position of mouse. The unit is swing (pixel).
     * @param mouseY The y position of mouse. The unit is swing (pixel).
     * @param androidWidth The width of rectangle. The unit is android dp.
     * @param androidHeight The height of rectangle. The unit is android dp.
     */
    fun updateValues(
      @SwingCoordinate x: Int,
      @SwingCoordinate y: Int,
      @SwingCoordinate w: Int,
      @SwingCoordinate h: Int,
      @SwingCoordinate mouseX: Int,
      @SwingCoordinate mouseY: Int,
      @AndroidDpCoordinate androidWidth: Int,
      @AndroidDpCoordinate androidHeight: Int,
    ) {
      this.x = x
      this.y = y
      this.w = w
      this.h = h
      this.mouseX = mouseX
      this.mouseY = mouseY
      this.androidWidth = androidWidth
      this.androidHeight = androidHeight
    }

    override fun paint(gc: Graphics2D) {
      drawLasso(gc, colorSet, x, y, w, h, mouseX, mouseY, androidWidth, androidHeight, true)
    }
  }
}
