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

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.InspectorModel.SelectionListener
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.PopupHandler
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope

/**
 * Panel responsible for rendering the [RenderModel] into a [Graphics] object and reacting to mouse
 * and keyboard events.
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
  private val coroutineScope: CoroutineScope,
  private val renderLogic: RenderLogic,
  private val renderModel: RenderModel,
  private val notificationModel: NotificationModel,
  private val displayRectangleProvider: () -> Rectangle?,
  private val screenScaleProvider: () -> Double,
  private val orientationQuadrantProvider: () -> Int,
  private val navigateToSelectedViewOnDoubleClick: () -> Unit,
) : LayoutInspectorRenderer() {

  override var interceptClicks = false
    set(value) {
      field = value

      if (!value) {
        // TODO(b/406518519): remove this once this class is refactored to use OnDeviceRendererModel
        // Clear selection and hover to avoid keeping a selected rectangles in the ui, that would be
        // un-selectable since clicks are not being intercepted.
        renderModel.model.setSelection(null, SelectionOrigin.INTERNAL)
        renderModel.model.hoveredNode = null
      }
    }

  private val repaintDisplayView = { refresh() }

  private val selectionChangedListener = SelectionListener { _, _, _ -> refresh() }

  companion object {
    private const val RENDERING_NOT_SUPPORTED_ID = "rendering.in.secondary.display.not.supported"
  }

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

    // re-render each time Layout Inspector model changes
    renderModel.modificationListeners.add(repaintDisplayView)

    renderModel.model.addSelectionListener(selectionChangedListener)
  }

  override fun dispose() {
    renderModel.modificationListeners.remove(repaintDisplayView)
    renderModel.model.removeSelectionListener(selectionChangedListener)
  }

  fun clearSelection() {
    renderModel.clearSelection()
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
    val layoutInspectorScreenDimension = renderModel.model.screenDimension
    // The rectangle containing LI rendering, in device scale.
    val layoutInspectorDisplayRectangle =
      Rectangle(0, 0, layoutInspectorScreenDimension.width, layoutInspectorScreenDimension.height)

    val scale = calculateScaleDifference(displayRectangle, layoutInspectorDisplayRectangle)
    val orientationQuadrant = orientationQuadrantProvider()

    // Make sure that borders and labels are scaled accordingly to the size of the render.
    renderLogic.renderSettings.scalePercent = (scale * 100).toInt()

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

  /**
   * Calculate the scale difference between [displayRectangle] and
   * [layoutInspectorDisplayRectangle]. This function assumes that the two rectangles are the same
   * rectangle, at different scale.
   *
   * @return A scale such that [layoutInspectorDisplayRectangle] * scale is equal to
   *   [displayRectangle].
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

  override fun paint(g: Graphics) {
    super.paint(g)

    val g2d = g.create() as Graphics2D

    // TODO(b/293584238) Remove once we support rendering on multiple displays.
    val notificationId = RENDERING_NOT_SUPPORTED_ID
    if (renderModel.model.resourceLookup.isRunningInMainDisplay == false) {
      if (!notificationModel.hasNotification(notificationId)) {
        notificationModel.addNotification(
          id = notificationId,
          text = LayoutInspectorBundle.message(notificationId),
          status = EditorNotificationPanel.Status.Warning,
          actions = emptyList(),
        )
      }
      // Do no render view bounds, because they would be on the wrong display.
      return
    } else {
      if (notificationModel.hasNotification(notificationId)) {
        notificationModel.removeNotification(notificationId)
      }
    }

    val displayRectangle = displayRectangleProvider() ?: return

    // Scale the display rectangle from physical to logical pixels.
    val physicalToLogicalScale = 1.0 / screenScaleProvider()
    val scaledDisplayRectangle = displayRectangle.scale(physicalToLogicalScale)

    val transform = getTransform(scaledDisplayRectangle)
    g2d.transform = g2d.transform.apply { concatenate(transform) }

    renderLogic.renderBorders(g2d, this, foreground)
    renderLogic.renderOverlay(g2d)
  }

  /** Transform panel coordinates to model coordinates. */
  private fun toModelCoordinates(originalCoordinates: Point2D): Point2D? {
    // TODO(b/293584238) Remove once we support rendering on multiple displays.
    if (renderModel.model.resourceLookup.isRunningInMainDisplay == false) {
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

  private inner class LayoutInspectorPopupHandler : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      if (!interceptClicks) return
      val modelCoordinates =
        toModelCoordinates(Point2D.Double(x.toDouble(), y.toDouble())) ?: return
      val views = renderModel.findViewsAt(modelCoordinates.x, modelCoordinates.y)
      showViewContextMenu(views.toList(), renderModel.model, this@StudioRendererPanel, x, y)
    }
  }

  private inner class LayoutInspectorMouseListener(private val renderModel: RenderModel) :
    MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return
      renderModel.selectView(modelCoordinates.x, modelCoordinates.y)

      if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
        navigateToSelectedViewOnDoubleClick()
      }

      refresh()
    }

    override fun mouseMoved(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return

      val modelCoordinates = toModelCoordinates(e.coordinates()) ?: return

      val hoveredNodeDrawInfo =
        renderModel.findDrawInfoAt(modelCoordinates.x, modelCoordinates.y).firstOrNull()
      renderModel.model.hoveredNode =
        hoveredNodeDrawInfo?.node?.findFilteredOwner(renderModel.treeSettings)

      refresh()
    }
  }
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
