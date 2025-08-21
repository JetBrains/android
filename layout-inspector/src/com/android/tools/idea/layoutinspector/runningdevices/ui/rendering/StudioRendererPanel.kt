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

import com.android.adblib.utils.createChildScope
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.resource.data.Display
import com.android.tools.idea.layoutinspector.ui.HQ_RENDERING_HINTS
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.scale.JBUIScale
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val RENDERING_NOT_SUPPORTED_ID = "rendering.in.secondary.display.not.supported"

/**
 * Panel responsible for rendering the [EmbeddedRendererModel] into a [Graphics] object and reacting
 * to mouse and keyboard events.
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
 */
class StudioRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  private val displayId: Int,
  private val renderModel: EmbeddedRendererModel,
  private val notificationModel: NotificationModel,
  private val displayRectangleProvider: () -> Rectangle?,
  private val screenScaleProvider: () -> Double,
  private val orientationQuadrantProvider: () -> Int,
) : LayoutInspectorRenderer() {

  private val childScope = scope.createChildScope()

  @VisibleForTesting
  var interceptClicks: Boolean
    get() = renderModel.interceptClicks.value
    set(value) {
      renderModel.setInterceptClicks(value)
    }

  private var overlay: Image? = null

  private var selectedNode: DrawInstruction? = null
  private var hoveredNode: DrawInstruction? = null
  private var visibleNodes: List<DrawInstruction> = emptyList()
  private var recomposingNodes: List<DrawInstruction> = emptyList()

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    // Events are not dispatched to the parent if the child has a mouse listener. So we need to
    // manually forward them.
    ForwardingMouseListener({ parent }, { !interceptClicks }).also {
      addMouseListener(it)
      addMouseMotionListener(it)
      addMouseWheelListener(it)
    }
    LayoutInspectorMouseListener(renderModel).also {
      addMouseListener(it)
      addMouseMotionListener(it)
    }
    addMouseListener(LayoutInspectorPopupHandler())

    // TODO(b/438162147): add multi display support to overlay
    childScope.launch { renderModel.overlay.collect { updateOverlay(it) } }
    childScope.launch { renderModel.overlayAlpha.collect { refresh() } }
    childScope.launch { renderModel.interceptClicks.collect { refresh() } }
    childScope.launch {
      renderModel.selectedNode.collect {
        selectedNode = it.filter(displayId)
        refresh()
      }
    }
    childScope.launch {
      renderModel.hoveredNode.collect {
        hoveredNode = it.filter(displayId)
        refresh()
      }
    }
    childScope.launch {
      renderModel.visibleNodes.collect {
        visibleNodes = it.mapNotNull { node -> node.filter(displayId) }
        refresh()
      }
    }
    childScope.launch {
      renderModel.recomposingNodes.collect {
        recomposingNodes = it.mapNotNull { node -> node.filter(displayId) }
        refresh()
      }
    }
  }

  override fun dispose() {}

  override fun paint(g: Graphics) {
    super.paint(g)

    val g2d = g.create() as Graphics2D
    g2d.setRenderingHints(HQ_RENDERING_HINTS)

    // TODO(b/293584238) Remove once we support rendering on multiple displays.
    if (displayId != Display.MAIN_DISPLAY_ID) {
      showMultiDisplayNotSupportedNotification()
      // Do no render view bounds, because they would be on the wrong display.
      return
    } else {
      if (notificationModel.hasNotification(RENDERING_NOT_SUPPORTED_ID)) {
        notificationModel.removeNotification(RENDERING_NOT_SUPPORTED_ID)
      }
    }

    val displayRectangle = displayRectangleProvider() ?: return

    // Scale the display rectangle from physical to logical pixels.
    val physicalToLogicalScale = 1.0 / screenScaleProvider()
    val scaledDisplayRectangle = displayRectangle.scale(physicalToLogicalScale)

    val transform = getTransform(scaledDisplayRectangle)
    g2d.transform = g2d.transform.apply { concatenate(transform) }

    // The order of the draw operations matters.
    if (overlay != null) {
      val bounds = renderModel.inspectorModel.root.layoutBounds
      g2d.composite = AlphaComposite.SrcOver.derive(renderModel.overlayAlpha.value)
      g2d.drawImage(overlay, bounds.x, bounds.y, bounds.width, bounds.height, null)
    }
    recomposingNodes.forEach { it.paint(g2d, fill = true) }
    visibleNodes.forEach { it.paint(g2d) }
    hoveredNode?.paint(g2d)
    selectedNode?.paint(g2d)
  }

  /**
   * Paints this [DrawInstruction] on the [graphics] context. The order of the draw operations in
   * this function matters.
   */
  private fun DrawInstruction.paint(graphics: Graphics2D, fill: Boolean = false) {
    // Thickness of the bounds.
    val boundsStrokeThickness = strokeThickness.scale()
    // Thickness of the outline of the bounds.
    val outlineStrokeThickness = boundsStrokeThickness / 2

    if (outlineColor != null) {
      // Draw the outline.
      graphics.color = JBColor(outlineColor, outlineColor)
      graphics.stroke = BasicStroke(outlineStrokeThickness)
      val outlineRect =
        Rectangle2D.Float(
          bounds.x - boundsStrokeThickness / 2 - outlineStrokeThickness / 2,
          bounds.y - boundsStrokeThickness / 2 - outlineStrokeThickness / 2,
          bounds.width + boundsStrokeThickness + outlineStrokeThickness,
          bounds.height + boundsStrokeThickness + outlineStrokeThickness,
        )
      graphics.draw(outlineRect)
    }

    // Draw the label.
    label?.paint(
      graphics = graphics,
      nodeBounds = bounds,
      boundsStrokeThickness = boundsStrokeThickness,
      outlineStrokeThickness = outlineStrokeThickness,
      backgroundColor = JBColor(color, color),
      textColor = JBColor(Color.WHITE, Color.WHITE),
      outlineColor = outlineColor?.let { JBColor(it, it) },
    )

    graphics.color = JBColor(color, color)
    graphics.stroke = BasicStroke(boundsStrokeThickness)

    // Draw the bounds.
    if (fill) {
      graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    } else {
      graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
    }
  }

  /**
   * Paints this [DrawInstruction.Label] on the [graphics] context. The order of the draw operations
   * in this function matters.
   */
  private fun DrawInstruction.Label.paint(
    graphics: Graphics2D,
    nodeBounds: Rectangle,
    boundsStrokeThickness: Float,
    outlineStrokeThickness: Float,
    backgroundColor: Color,
    textColor: Color,
    outlineColor: Color?,
  ) {
    graphics.font = graphics.font.deriveFont(this.size.scale())
    val fontMetrics = graphics.fontMetrics

    // Distance between the label text and the label borders.
    val padding = 8f.scale()
    val textWidth = fontMetrics.stringWidth(this.text)
    val textHeight = fontMetrics.maxAscent

    val labelWidth = textWidth + 2 * padding
    val labelHeight = textHeight + 2 * padding

    var labelLeft = nodeBounds.x - boundsStrokeThickness / 2
    var labelBottom = nodeBounds.y - boundsStrokeThickness / 2
    var labelTop = labelBottom - labelHeight
    var labelRight = labelLeft + labelWidth

    // Use inverse transformation of the bounds to make them match the scale of draw instruction
    // bounds.
    val canvasBounds = graphics.transform.createInverse().createTransformedShape(bounds).bounds2D

    if (labelLeft < canvasBounds.x) {
      // If it extends beyond the left edge of the canvas, move it right so it fits.
      labelLeft = canvasBounds.x.toFloat()
      labelRight = labelLeft + labelWidth
    }
    if (labelTop < canvasBounds.y) {
      // If the text goes above the top edge of the canvas, move it down so it fits.
      labelTop = canvasBounds.y.toFloat()
      labelBottom = labelTop + labelHeight
    }

    // Use float rectangle to avoid rounding errors resulting from float to int conversion.
    val labelBounds =
      Rectangle2D.Float(labelLeft, labelTop, labelRight - labelLeft, labelBottom - labelTop)

    if (outlineColor != null) {
      // Draw the outline around the label.
      graphics.color = outlineColor
      graphics.stroke = BasicStroke(outlineStrokeThickness)
      val outlineRect =
        Rectangle2D.Float(
          labelBounds.x - outlineStrokeThickness / 2,
          labelBounds.y - outlineStrokeThickness / 2,
          labelBounds.width + outlineStrokeThickness,
          labelBounds.height + outlineStrokeThickness,
        )
      graphics.draw(outlineRect)
    }

    // Draw the label.
    graphics.color = backgroundColor
    graphics.fill(labelBounds)

    // Draw the label's text.
    graphics.color = textColor
    graphics.drawString(this.text, labelLeft + padding, labelBottom - padding)
  }

  private fun refresh() {
    revalidate()
    repaint()
  }

  /**
   * Transform the rendering from LI to match the display rendering from Running Devices. This
   * function assumes the rendering from LI starts a coordinates (0, 0).
   *
   * @param displayRectangle The rectangle from Running Devices, on which the device display is
   *   rendered.
   */
  private fun getTransform(displayRectangle: Rectangle): AffineTransform {
    val layoutInspectorScreenDimension = renderModel.inspectorModel.getDisplayDimension(displayId)
    // The rectangle containing LI rendering, in device scale.
    val layoutInspectorDisplayRectangle =
      Rectangle(0, 0, layoutInspectorScreenDimension.width, layoutInspectorScreenDimension.height)

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

  private fun updateOverlay(byteArray: ByteArray?) {
    if (byteArray != null) {
      overlay = ImageIO.read(ByteArrayInputStream(byteArray))
      refresh()
    }
  }

  private inner class LayoutInspectorPopupHandler : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      if (!interceptClicks) return
      val modelCoordinates =
        toModelCoordinates(Point2D.Double(x.toDouble(), y.toDouble())) ?: return
      val views = renderModel.findNodesAt(modelCoordinates.x, modelCoordinates.y)
      showViewContextMenu(
        views = views.toList(),
        inspectorModel = renderModel.inspectorModel,
        source = this@StudioRendererPanel,
        x = x,
        y = y,
      )
    }
  }

  private inner class LayoutInspectorMouseListener(private val renderModel: EmbeddedRendererModel) :
    MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return
      renderModel.selectNode(modelCoordinates.x, modelCoordinates.y)

      if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
        renderModel.doubleClickNode(modelCoordinates.x, modelCoordinates.y)
      }
    }

    override fun mouseMoved(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return

      renderModel.hoverNode(modelCoordinates.x, modelCoordinates.y)
    }
  }

  /** Transform panel coordinates to model coordinates. */
  private fun toModelCoordinates(originalCoordinates: Point2D): Point2D? {
    // TODO(b/293584238) Remove once we support rendering on multiple displays.
    if (displayId != Display.MAIN_DISPLAY_ID) {
      // Do no render provide coordinates, because they would be on the wrong display.
      return null
    }

    val scaledCoordinates = originalCoordinates.scale(screenScaleProvider())
    val transformedPoint2D = Point2D.Double()

    val displayRectangle = displayRectangleProvider() ?: return null
    val transform = getTransform(displayRectangle)
    transform.inverseTransform(scaledCoordinates, transformedPoint2D)

    return transformedPoint2D
  }

  private fun Float.scale(): Float {
    return JBUIScale.scale(this) / renderModel.renderSettings.scaleFraction.toFloat()
  }

  private fun showMultiDisplayNotSupportedNotification() {
    if (!notificationModel.hasNotification(RENDERING_NOT_SUPPORTED_ID)) {
      notificationModel.addNotification(
        id = RENDERING_NOT_SUPPORTED_ID,
        text = LayoutInspectorBundle.message(RENDERING_NOT_SUPPORTED_ID),
        status = EditorNotificationPanel.Status.Warning,
        actions = emptyList(),
      )
    }
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

private fun Point2D.scale(scale: Double) = Point2D.Double(x * scale, y * scale)

private fun MouseEvent.coordinates() = Point2D.Double(x.toDouble(), y.toDouble())

private fun DrawInstruction?.filter(displayId: Int): DrawInstruction? {
  return if (this?.displayId == displayId) {
    this
  } else {
    null
  }
}
