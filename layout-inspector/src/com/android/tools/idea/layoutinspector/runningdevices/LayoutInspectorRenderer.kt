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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

/**
 * Class responsible for rendering the [RenderModel] into a [Graphics] object.
 * Renders the borders and the overlay, see [RenderLogic.renderBorders] and [RenderLogic.renderOverlay].
 *
 * @param component The component on which we are rendering.
 * It is used to access swing properties that this class wouldn't know how to access otherwise.
 */
class LayoutInspectorRenderer(
  private val renderLogic: RenderLogic,
  private val renderModel: RenderModel,
  private val component: Component,
  private val displayRectangleProvider: () -> Rectangle?,
  private val screenScaleProvider: () -> Double
) {

  init {
    // TODO(b/265150325) when running devices the zoom does not affect the scale. Move this somewhere else.
    renderLogic.renderSettings.scalePercent = 30

    val layoutInspectorMouseListener = LayoutInspectorMouseListener(renderModel)
    component.addMouseListener(layoutInspectorMouseListener)
    component.addMouseMotionListener(layoutInspectorMouseListener)
  }

  /**
   * Transform to the center of the panel and scale Layout Inspector UI to display size.
   * @param displayRectangle The rectangle on which the device display is rendered.
   */
  private fun getTransform(displayRectangle: Rectangle): AffineTransform {
    val rootView = renderModel.model.root

    // calculate how much we need to scale the Layout Inspector bounds to match the device frame.
    val scale = displayRectangle.width.toDouble() / rootView.layoutBounds.bounds.width.toDouble()

    return AffineTransform().apply {
      // translate to center of panel
      translate(displayRectangle.x + displayRectangle.width / 2.0, displayRectangle.y + displayRectangle.height / 2.0)
      scale(scale, scale)
    }
  }

  fun paint(g: Graphics, displayRectangle: Rectangle) {
    val g2d = g as Graphics2D
    g2d.color = primaryPanelBackground

    val transform = getTransform(displayRectangle)
    g2d.transform = g2d.transform.apply { concatenate(transform) }

    renderLogic.renderBorders(g2d, component, component.foreground)
    renderLogic.renderOverlay(g2d)
  }

  /**
   * Transform panel coordinates to model coordinates.
   */
  private fun toModelCoordinates(originalCoordinates: Point2D): Point2D? {
    val scaledCoordinates = originalCoordinates.scale(screenScaleProvider())
    val transformedPoint2D = Point2D.Double()

    val displayRectangle = displayRectangleProvider() ?: return null
    val transform = getTransform(displayRectangle)
    transform.inverseTransform(scaledCoordinates, transformedPoint2D)

    return transformedPoint2D
  }

  private inner class LayoutInspectorMouseListener(private val renderModel: RenderModel) : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (e.isConsumed) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return

      val hoveredNodeDrawInfo = renderModel.findDrawInfoAt(modelCoordinates.x, modelCoordinates.y).firstOrNull()
      renderModel.model.hoveredNode = hoveredNodeDrawInfo?.node?.findFilteredOwner(renderModel.treeSettings)

      component.invalidate()
      component.repaint()
    }
  }
}

private fun Point2D.scale(scale: Double) = Point2D.Double(x * scale, y * scale)
private fun MouseEvent.coordinates() = Point2D.Double(x.toDouble(), y.toDouble())