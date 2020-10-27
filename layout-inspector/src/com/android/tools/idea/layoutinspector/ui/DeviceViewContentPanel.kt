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

import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

private const val MARGIN = 50


class DeviceViewContentPanel(val inspectorModel: InspectorModel, val viewSettings: DeviceViewSettings) : AdtPrimaryPanel() {

  val model = DeviceViewPanelModel(inspectorModel)

  val rootLocation: Point
    get() {
      val modelLocation = model.hitRects.firstOrNull()?.bounds?.bounds?.location ?: Point(0,0)
      return Point((modelLocation.x * viewSettings.scaleFraction).toInt() + (size.width / 2),
                   (modelLocation.y * viewSettings.scaleFraction).toInt() + (size.height / 2))
    }

  private val HQ_RENDERING_HINTS = mapOf(
    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
  )

  init {
    inspectorModel.selectionListeners.add { _, _ -> repaint() }
    inspectorModel.hoverListeners.add { _, _ -> repaint() }
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        repaint()
      }
    })

    val listener = object : MouseAdapter() {
      private var x = 0
      private var y = 0

      override fun mousePressed(e: MouseEvent) {
        if (e.isConsumed) return
        x = e.x
        y = e.y
      }

      override fun mouseDragged(e: MouseEvent) {
        if (e.isConsumed) return
        if (!model.rotatable) {
          // can't rotate
          return
        }
        val xRotation = (e.x - x) * 0.001
        val yRotation = (e.y - y) * 0.001
        x = e.x
        y = e.y
        if (xRotation != 0.0 || yRotation != 0.0) {
          model.rotate(xRotation, yRotation)
        }
        repaint()
      }

      private fun nodeAtPoint(e: MouseEvent) = model.findTopViewAt((e.x - size.width / 2.0) / viewSettings.scaleFraction,
                                                                 (e.y - size.height / 2.0) / viewSettings.scaleFraction)

      override fun mouseClicked(e: MouseEvent) {
        if (e.isConsumed) return
        inspectorModel.selection = nodeAtPoint(e)
        inspectorModel.stats.selectionMadeFromImage()
      }

      override fun mouseMoved(e: MouseEvent) {
        if (e.isConsumed) return
        inspectorModel.hoveredNode = findTopViewAt(e.x, e.y)
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        showViewContextMenu(findComponentsAt(x, y).toList(), inspectorModel, this@DeviceViewContentPanel, x, y)
      }
    })

    viewSettings.modificationListeners.add { repaint() }
    model.modificationListeners.add {
      revalidate()
      repaint()
    }
  }

  private fun findComponentsAt(x: Int, y: Int) = model.findViewsAt((x - size.width / 2.0) / viewSettings.scaleFraction,
                                                                      (y - size.height / 2.0) / viewSettings.scaleFraction)

  private fun findTopViewAt(x: Int, y: Int) = model.findTopViewAt((x - size.width / 2.0) / viewSettings.scaleFraction,
                                                                  (y - size.height / 2.0) / viewSettings.scaleFraction)

  override fun paint(g: Graphics?) {
    val g2d = g as? Graphics2D ?: return
    g2d.color = background
    g2d.fillRect(0, 0, width, height)
    g2d.setRenderingHints(HQ_RENDERING_HINTS)
    g2d.translate(size.width / 2.0, size.height / 2.0)
    g2d.scale(viewSettings.scaleFraction, viewSettings.scaleFraction)

    model.hitRects.forEach { drawImages(g2d, it) }
    model.hitRects.forEach { drawBorders(g2d, it) }

    if (model.overlay != null) {
      g2d.composite = AlphaComposite.SrcOver.derive(model.overlayAlpha)
      val bounds = model.hitRects[0].bounds.bounds
      g2d.drawImage(model.overlay, bounds.x, bounds.y, bounds.width, bounds.height, null)
    }
  }

  override fun getPreferredSize() =
    if (inspectorModel.isEmpty) Dimension(0, 0)
    // Give twice the needed size so we have room to move the view around a little. Otherwise things can jump around
    // when the number of layers changes and the canvas size adjusts to smaller than the viewport size.
    else Dimension((model.maxWidth * viewSettings.scaleFraction + JBUI.scale(MARGIN)).toInt() * 2,
                   (model.maxHeight * viewSettings.scaleFraction + JBUI.scale(MARGIN)).toInt() * 2)

  private fun drawBorders(g: Graphics2D, drawInfo: ViewDrawInfo) {
    val hoveredNode = inspectorModel.hoveredNode

    val drawView = drawInfo.node
    val view = drawView.owner
    val selection = inspectorModel.selection

    if (!drawInfo.isCollapsed &&
        (viewSettings.drawBorders || viewSettings.drawUntransformedBounds || view == selection || view == hoveredNode)) {
      val g2 = g.create() as Graphics2D
      g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }
      drawView.paintBorder(g2, view == selection, view == hoveredNode, viewSettings)
    }
  }

  private fun drawImages(g: Graphics, drawInfo: ViewDrawInfo) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }
    drawInfo.node.paint(g2, inspectorModel)
  }
}