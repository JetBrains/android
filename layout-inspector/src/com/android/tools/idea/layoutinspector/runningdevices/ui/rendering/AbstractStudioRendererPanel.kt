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
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.ui.HQ_RENDERING_HINTS
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
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
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Base class for a studio-side renderer (non on-device renderer) that is driven by an
 * [EmbeddedRendererModel].
 */
abstract class AbstractStudioRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  protected val renderModel: EmbeddedRendererModel,
) : LayoutInspectorRenderer() {

  protected val childScope = scope.createChildScope()
  private var overlay: Image? = null

  @VisibleForTesting abstract val interceptClicks: Boolean

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    LayoutInspectorMouseListener().also {
      addMouseListener(it)
      addMouseMotionListener(it)
    }
    addMouseListener(LayoutInspectorPopupHandler())

    // TODO(b/438162147): add multi display support to overlay
    childScope.launch { renderModel.overlay.collect { updateOverlay(it) } }
    childScope.launch { renderModel.overlayAlpha.collect { refresh() } }
    childScope.launch { renderModel.interceptClicks.collect { refresh() } }
    childScope.launch { renderModel.selectedNode.collect { refresh() } }
    childScope.launch { renderModel.hoveredNode.collect { refresh() } }
    childScope.launch { renderModel.visibleNodes.collect { refresh() } }
    childScope.launch { renderModel.recomposingNodes.collect { refresh() } }

    renderModel.setInterceptClicks(interceptClicks)
  }

  override fun dispose() {}

  protected fun refresh() {
    revalidate()
    repaint()
  }

  private fun updateOverlay(byteArray: ByteArray?) {
    overlay =
      if (byteArray != null) {
        ImageIO.read(ByteArrayInputStream(byteArray))
      } else {
        null
      }
    refresh()
  }

  /** Calculate the transform for this render cycle. Returns null if rendering should be skipped. */
  protected abstract fun getRenderTransform(): AffineTransform?

  /** Calculate the bounds where the overlay image should be drawn. */
  protected abstract fun getOverlayBounds(transform: AffineTransform): Rectangle?

  override fun paint(g: Graphics) {
    super.paint(g)

    val transform = getRenderTransform() ?: return

    val g2d = g.create() as Graphics2D
    g2d.setRenderingHints(HQ_RENDERING_HINTS)
    g2d.transform = g2d.transform.apply { concatenate(transform) }

    doPaint(g2d, transform)
  }

  /** Paints Layout Inspector UI. The order of the draw operation matters. */
  private fun doPaint(g2d: Graphics2D, transform: AffineTransform) {
    if (overlay != null) {
      getOverlayBounds(transform)?.let { overlayBounds ->
        g2d.drawImage(
          image = overlay!!,
          bounds = overlayBounds,
          alpha = renderModel.overlayAlpha.value,
        )
      }
    }

    val scale = renderModel.renderSettings.scaleFraction.toFloat()
    // Apply inverse transformation to canvas bounds, to make them match the scale of the draw
    // instruction bounds.
    val canvasBounds = g2d.transform.createInverse().createTransformedShape(bounds).bounds2D

    renderModel.recomposingNodes.value.forEach {
      it.paint(g2d, canvasBounds = canvasBounds, scale = scale, fill = true)
    }
    renderModel.visibleNodes.value.forEach {
      it.paint(g2d, canvasBounds = canvasBounds, scale = scale)
    }
    renderModel.hoveredNode.value?.paint(g2d, canvasBounds = canvasBounds, scale = scale)
    renderModel.selectedNode.value?.paint(g2d, canvasBounds = canvasBounds, scale = scale)
  }

  /** Transform panel coordinates to model coordinates. */
  protected fun toModelCoordinates(originalCoordinates: Point2D): Point2D? {
    val transform = getRenderTransform() ?: return null
    val transformedPoint2D = Point2D.Double()
    try {
      transform.inverseTransform(originalCoordinates, transformedPoint2D)
      return transformedPoint2D
    } catch (_: NoninvertibleTransformException) {
      return null
    }
  }

  private inner class LayoutInspectorPopupHandler : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      if (!interceptClicks) return
      val modelCoordinates =
        toModelCoordinates(Point2D.Double(x.toDouble(), y.toDouble())) ?: return
      val views = renderModel.rightClickNode(modelCoordinates.x, modelCoordinates.y)
      showViewContextMenu(
        selectedView = renderModel.inspectorModel.selection,
        views = views.toList(),
        inspectorModel = renderModel.inspectorModel,
        source = this@AbstractStudioRendererPanel,
        x = x,
        y = y,
      )
    }
  }

  private inner class LayoutInspectorMouseListener : MouseAdapter() {
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

    override fun mouseExited(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return
      renderModel.clearHoverNode()
    }
  }
}

private fun MouseEvent.coordinates() = Point2D.Double(x.toDouble(), y.toDouble())

/** Draw the [image] in the [Graphics2D] context, with the provided [alpha] value */
private fun Graphics2D.drawImage(image: Image, bounds: Rectangle, alpha: Float) {
  val previousComposite = composite
  composite = AlphaComposite.SrcOver.derive(alpha)
  drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null)
  // Restore the alpha
  composite = previousComposite
}

/**
 * Paints this [DrawInstruction] on the [graphics] context. The order of the draw operations in this
 * function matters.
 */
private fun DrawInstruction.paint(
  graphics: Graphics2D,
  canvasBounds: Rectangle2D,
  scale: Float,
  fill: Boolean = false,
) {
  // Thickness of the bounds.
  val boundsStrokeThickness = strokeThickness.scale(scale)
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
    scale = scale,
    canvasBounds = canvasBounds,
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
  scale: Float,
  canvasBounds: Rectangle2D,
) {
  graphics.font = graphics.font.deriveFont(this.size.scale(scale))
  val fontMetrics = graphics.fontMetrics

  // Distance between the label text and the label borders.
  val padding = 8f.scale(scale)
  val textWidth = fontMetrics.stringWidth(this.text)
  val textHeight = fontMetrics.maxAscent

  val labelWidth = textWidth + 2 * padding
  val labelHeight = textHeight + 2 * padding

  var labelLeft = nodeBounds.x - boundsStrokeThickness / 2
  var labelBottom = nodeBounds.y - boundsStrokeThickness / 2
  var labelTop = labelBottom - labelHeight
  var labelRight = labelLeft + labelWidth

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

private fun Float.scale(scale: Float): Float {
  return JBUIScale.scale(this) / scale
}
