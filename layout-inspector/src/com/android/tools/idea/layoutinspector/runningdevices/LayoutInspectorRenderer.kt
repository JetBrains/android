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

import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import kotlinx.coroutines.CoroutineScope
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import javax.swing.JPanel

/**
 * Panel responsible for rendering the [RenderModel] into a [Graphics] object and reacting to mouse and keyboard events.
 * @param displayRectangleProvider Returns the rectangle of the device screen. In physical pixels.
 * If used for rendering it needs to be scaled to logical pixels.
 * A Physical pixel corresponds to a real pixel on the display. A logical pixel corresponds to a physical pixels * screen scale.
 * For example on a Retina display a logical pixel is a physical pixel * 2.
 * @param screenScaleProvider Returns the screen scale. For example 1 on a regular display and 2 on a Retina display.
 */
class LayoutInspectorRenderer(
  disposable: Disposable,
  private val coroutineScope: CoroutineScope,
  private val renderLogic: RenderLogic,
  private val renderModel: RenderModel,
  private val displayRectangleProvider: () -> Rectangle?,
  private val screenScaleProvider: () -> Double,
  private val currentSessionStatistics: () -> SessionStatistics
): JPanel(), Disposable {

  var interceptClicks = false

  private val repaintDisplayView = { refresh() }

  fun interface RefreshListener {
    fun onRefresh()
  }

  private val listeners = mutableListOf<RefreshListener>()

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    // TODO(b/265150325) when running devices the zoom does not affect the scale. Move this somewhere else.
    renderLogic.renderSettings.scalePercent = 30

    // Events are not dispatched to the parent if the child has a mouse listener. So we need to manually forward them.
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
    LayoutInspectorDoubleClickListener().installOn(this)

    // re-render each time Layout Inspector model changes
    renderModel.modificationListeners.add(repaintDisplayView)
  }

  override fun dispose() {
    renderModel.modificationListeners.remove(repaintDisplayView)
  }

  fun refresh() {
    revalidate()
    repaint()
    listeners.forEach { it.onRefresh() }
  }

  fun addListener(listener: RefreshListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: RefreshListener) {
    listeners.remove(listener)
  }

  /**
   * Transform to the center of the panel and scale Layout Inspector UI to display size.
   * @param displayRectangle The rectangle on which the device display is rendered.
   */
  private fun getTransform(displayRectangle: Rectangle): AffineTransform {
    // calculate how much we need to scale the Layout Inspector bounds to match the device frame.
    val scale = displayRectangle.width.toDouble() / renderModel.model.screenDimension.width.toDouble()

    val screenScaled = renderModel.model.screenDimension.scale(scale)
    val rootBoundsScaled = renderModel.model.root.layoutBounds.scale(scale)

    val leftBorderScaled = rootBoundsScaled.x
    val topBorderScaled = rootBoundsScaled.y
    val rightBorderScaled = screenScaled.width - (rootBoundsScaled.x + rootBoundsScaled.width)
    val bottomBorderScaled = screenScaled.height - (rootBoundsScaled.y + rootBoundsScaled.height)

    return AffineTransform().apply {
      // Translate to be centered with the rendering from Running Devices.
      translate((displayRectangle.x + displayRectangle.width / 2.0), displayRectangle.y + displayRectangle.height / 2.0)
      // Translate to keep into account potential borders on the sides of the display.
      // For example if the device has a notch, in landscape node the UI might not be rendered in that area.
      // So if, for example, the border is on the left, we need to translate the view bounds to the right.
      translate((leftBorderScaled - rightBorderScaled) / 2.0, (topBorderScaled - bottomBorderScaled) / 2.0)
      scale(scale, scale)
    }
  }

  override fun paint(g: Graphics) {
    super.paint(g)

    val g2d = g.create() as Graphics2D

    val displayRectangle = displayRectangleProvider() ?: return

    // Scale the display rectangle from physical to logical pixels.
    val physicalToLogicalScale = 1.0 / screenScaleProvider()
    val scaledDisplayRectangle = displayRectangle.scale(physicalToLogicalScale)

    val transform = getTransform(scaledDisplayRectangle)
    g2d.transform = g2d.transform.apply { concatenate(transform) }

    renderLogic.renderBorders(g2d, this, foreground)
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

  private inner class LayoutInspectorPopupHandler : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      if (!interceptClicks) return
      val modelCoordinates = toModelCoordinates(Point2D.Double(x.toDouble(), y.toDouble())) ?: return
      val views = renderModel.findViewsAt(modelCoordinates.x, modelCoordinates.y)
      showViewContextMenu(views.toList(), renderModel.model, this@LayoutInspectorRenderer, x, y)
    }
  }

  private inner class LayoutInspectorDoubleClickListener : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      if (!interceptClicks) return false

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return false
      renderModel.selectView(modelCoordinates.x, modelCoordinates.y)
      // Navigate to sources on double click.
      // TODO(b/265150325) move to RenderModel for consistency
      GotoDeclarationAction.navigateToSelectedView(coroutineScope, renderModel.model)
      currentSessionStatistics().gotoSourceFromRenderDoubleClick()
      return true
    }
  }

  private inner class LayoutInspectorMouseListener(
    private val renderModel: RenderModel
  ) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return
      renderModel.selectView(modelCoordinates.x, modelCoordinates.y)

      refresh()
    }

    override fun mouseMoved(e: MouseEvent) {
      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return

      val hoveredNodeDrawInfo = renderModel.findDrawInfoAt(modelCoordinates.x, modelCoordinates.y).firstOrNull()
      renderModel.model.hoveredNode = hoveredNodeDrawInfo?.node?.findFilteredOwner(renderModel.treeSettings)

      refresh()
    }
  }
}

/**
 * A mouse listener that forwards its events to the component provided by [componentProvider] if [shouldForward] returns true.
 */
private class ForwardingMouseListener(
  private val componentProvider: () -> Component,
  private val shouldForward: () -> Boolean
) : MouseListener, MouseWheelListener, MouseMotionListener {
  override fun mouseClicked(e: MouseEvent) = forwardEvent(e)
  override fun mousePressed(e: MouseEvent) = forwardEvent(e)
  override fun mouseReleased(e: MouseEvent) = forwardEvent(e)
  override fun mouseEntered(e: MouseEvent) = forwardEvent(e)
  override fun mouseExited(e: MouseEvent) = forwardEvent(e)
  override fun mouseWheelMoved(e: MouseWheelEvent) = forwardEvent(e)
  override fun mouseDragged(e: MouseEvent) = forwardEvent(e)
  override fun mouseMoved(e: MouseEvent) = forwardEvent(e)

  private fun forwardEvent(e: MouseEvent) {
    if (shouldForward()) {
      componentProvider().dispatchEvent(e)
    }
  }
}

private fun Dimension.scale(scale: Double) = Dimension((width * scale).toInt(), (height * scale).toInt())

private fun Rectangle.scale(physicalToLogicalScale: Double): Rectangle {
  return Rectangle((x * physicalToLogicalScale).toInt(), (y * physicalToLogicalScale).toInt(), (width * physicalToLogicalScale).toInt(), (height *physicalToLogicalScale).toInt())
}

private fun Point2D.scale(scale: Double) = Point2D.Double(x * scale, y * scale)
private fun MouseEvent.coordinates() = Point2D.Double(x.toDouble(), y.toDouble())