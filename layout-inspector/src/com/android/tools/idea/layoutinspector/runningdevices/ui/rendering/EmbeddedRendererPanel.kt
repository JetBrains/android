/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope

/**
 * Panel responsible for rendering Layout Inspector UI for embedded Layout Inspector.
 *
 * @param displayRectangleProvider Returns the rectangle of the device screen. In physical pixels.
 *   If used for rendering it needs to be scaled to logical pixels. A Physical pixel corresponds to
 *   a real pixel on the display. A logical pixel corresponds to a physical pixels * screen scale.
 *   For example on a Retina display a logical pixel is a physical pixel * 2.
 * @param screenScaleProvider Returns the screen scale. For example 1 on a regular display and 2 on
 *   a Retina display.
 * @param orientationQuadrantProvider Returns an integer that indicates the rotation that should be
 *   applied to the Layout Inspector's rendering in order to match the rendering from Running
 *   Devices.
 * @param deviceDisplayDimensionProvider Returns the dimension of the device display, as known by
 *   Layout Inspector.
 */
class EmbeddedRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  renderModel: EmbeddedRendererModel,
  private val displayRectangleProvider: () -> Rectangle?,
  private val screenScaleProvider: () -> Double,
  private val orientationQuadrantProvider: () -> Int,
  private val deviceDisplayDimensionProvider: () -> Dimension,
) : AbstractStudioRendererPanel(disposable, scope, renderModel) {

  override val interceptClicks: Boolean
    get() = renderModel.interceptClicks.value

  /** The rectangle delimiting the drawing area in the last render cycle. */
  private var currentDrawingArea: Rectangle? = null

  init {
    // Events are not dispatched to the parent if the child has a mouse listener. So we need to
    // manually forward them.
    ForwardingMouseListener({ parent }, { !interceptClicks }).also {
      addMouseListener(it)
      addMouseMotionListener(it)
      addMouseWheelListener(it)
    }
  }

  override fun getRenderTransform(): AffineTransform? {
    val displayRectangle = displayRectangleProvider() ?: return null

    // Scale the display rectangle from physical to logical pixels.
    val physicalToLogicalScale = 1.0 / screenScaleProvider()
    val scaledDisplayRectangle = displayRectangle.scale(physicalToLogicalScale)

    currentDrawingArea = scaledDisplayRectangle

    return calculateTransform(scaledDisplayRectangle)
  }

  override fun getOverlayBounds(transform: AffineTransform): Rectangle? {
    val drawingArea = currentDrawingArea ?: return null
    // revert the scale applied to the transform
    return transform.createInverse().createTransformedShape(drawingArea).bounds
  }

  /**
   * Scale and translate the view bounds from Layout Inspector to match the display rendering from
   * Running Devices. This function assumes the rendering from LI starts a coordinates (0, 0).
   */
  private fun calculateTransform(displayRectangle: Rectangle): AffineTransform {
    val deviceDisplayDimension = deviceDisplayDimensionProvider()
    // The rectangle containing LI rendering, in device scale.
    val layoutInspectorDisplayRectangle =
      Rectangle(0, 0, deviceDisplayDimension.width, deviceDisplayDimension.height)

    val scale = calculateScaleDifference(displayRectangle, layoutInspectorDisplayRectangle)
    val orientationQuadrant = orientationQuadrantProvider()

    // Make sure that borders and labels are scaled accordingly to the size of the render.
    renderModel.renderSettings.scalePercent = (scale * 100).toInt()

    val transform = AffineTransform()

    // Apply scale and rotation, this will transform LI rendering to match the rendering from RD, in
    // terms of scale and orientation.
    transform.apply {
      scale(scale, scale)
      quadrantRotate(orientationQuadrant)
    }

    // Create the new transformed shape of LI rendering. This will have same scale and orientation
    // as the display from RD.
    val deviceRectTrans = transform.createTransformedShape(layoutInspectorDisplayRectangle)

    // Calculate the distance between LI rendering and the display from RD.
    val xDelta = abs(displayRectangle.x - deviceRectTrans.bounds.x)
    val yDelta = abs(displayRectangle.y - deviceRectTrans.bounds.y)

    transform.apply {
      // Remove rotation, otherwise translate is affected by it.
      quadrantRotate(-orientationQuadrant)
      // Translate LI rendering to overlap with display from RD.
      translate(xDelta.toDouble() / scale, yDelta.toDouble() / scale)
      // Re-apply rotation.
      quadrantRotate(orientationQuadrant)
    }

    return transform
  }
}

/**
 * Calculate the scale difference between [displayRectangle] and [layoutInspectorDisplayRectangle].
 * This function assumes that the two rectangles are the same rectangle, at different scale.
 */
private fun calculateScaleDifference(
  displayRectangle: Rectangle,
  layoutInspectorDisplayRectangle: Rectangle,
): Double {
  // Get the biggest side of both rectangles and use them to calculate the difference in scale.
  // Using the biggest side makes sure that if the rotation of the two rectangles is not the same,
  // the scale difference is not affected.
  val displayMaxSide = max(displayRectangle.width, displayRectangle.height)
  val layoutInspectorDisplayMaxSide =
    max(layoutInspectorDisplayRectangle.width, layoutInspectorDisplayRectangle.height)

  return displayMaxSide.toDouble() / layoutInspectorDisplayMaxSide.toDouble()
}

private fun Rectangle.scale(physicalToLogicalScale: Double): Rectangle {
  return Rectangle(
    (x * physicalToLogicalScale).toInt(),
    (y * physicalToLogicalScale).toInt(),
    (width * physicalToLogicalScale).toInt(),
    (height * physicalToLogicalScale).toInt(),
  )
}
