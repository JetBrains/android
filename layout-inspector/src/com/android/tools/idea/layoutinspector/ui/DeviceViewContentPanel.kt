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
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
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
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D.WIND_NON_ZERO
import java.awt.geom.Rectangle2D

private const val MARGIN = 50

private const val NORMAL_BORDER_THICKNESS = 1f
private const val EMPHASIZED_BORDER_THICKNESS = 5f
private const val EMPHASIZED_BORDER_OUTLINE_THICKNESS = 7f
private const val LABEL_FONT_SIZE = 30f

private val EMPHASIZED_LINE_COLOR = Color(106, 161, 211)
private val EMPHASIZED_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS)
private val EMPHASIZED_LINE_OUTLINE_STROKE = BasicStroke(EMPHASIZED_BORDER_OUTLINE_THICKNESS)
private val SELECTED_LINE_COLOR = Color(24, 134, 247)
private val SELECTED_LINE_STROKE = EMPHASIZED_LINE_STROKE
private val NORMAL_LINE_COLOR = JBColor(Gray.get(128, 128), Gray.get(212, 128))
private val NORMAL_LINE_STROKE = BasicStroke(NORMAL_BORDER_THICKNESS)
private val EMPHASIZED_LINE_OUTLINE_COLOR = Color.white

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
    inspectorModel.modificationListeners.add(::modelChanged)
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
        // Get distinct views where only the last duplicate is retained (rather than the first as we'd get by distinct() alone).
        val views = findComponentsAt(x, y)
          .asReversed()
          .asSequence()
          .distinct()
          .toList()
          .asReversed()
        showViewContextMenu(views, inspectorModel, this@DeviceViewContentPanel, x, y)
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

    model.hitRects.forEach { drawView(g2d, it) }

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

  private fun drawView(g: Graphics,
                       drawInfo: ViewDrawInfo) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    val selection = inspectorModel.selection
    val drawView = drawInfo.node
    val view = drawView.owner
    val hoveredNode = inspectorModel.hoveredNode
    // TODO: what's hovered?
    if (viewSettings.drawBorders || view == selection || view == hoveredNode) {
      when (view) {
        selection, hoveredNode -> {
          g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
          g2.stroke = EMPHASIZED_LINE_OUTLINE_STROKE
          g2.draw(drawInfo.bounds)
        }
      }
      when (view) {
        selection -> {
          g2.color = SELECTED_LINE_COLOR
          g2.stroke = SELECTED_LINE_STROKE
        }
        hoveredNode -> {
          g2.color = EMPHASIZED_LINE_COLOR
          g2.stroke = EMPHASIZED_LINE_STROKE
        }
        else -> {
          g2.color = NORMAL_LINE_COLOR
          g2.stroke = NORMAL_LINE_STROKE
        }
      }
      g2.draw(drawInfo.bounds)
    }

    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }

    val origClip = g2.clip
    g2.clip(drawInfo.clip)
    drawInfo.node.paint(g2, inspectorModel)
    g2.clip = origClip
    if (viewSettings.drawLabel && view == selection && drawView is DrawViewChild) {
      g2.font = g2.font.deriveFont(JBUIScale.scale(LABEL_FONT_SIZE))
      val fontMetrics = g2.fontMetrics
      val width = fontMetrics.stringWidth(view.unqualifiedName)
      val height = fontMetrics.maxAscent + fontMetrics.maxDescent
      val border = height * 0.3f
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_LINE_OUTLINE_STROKE
      val outlinePath = GeneralPath(WIND_NON_ZERO)
      outlinePath.moveTo(view.x.toFloat(), view.y.toFloat() - EMPHASIZED_BORDER_OUTLINE_THICKNESS)
      outlinePath.lineTo(view.x.toFloat(),
                         view.y - height.toFloat() - border + EMPHASIZED_BORDER_THICKNESS)
      outlinePath.lineTo(view.x.toFloat() + width + 2f * border - EMPHASIZED_BORDER_THICKNESS,
                         view.y - height.toFloat() - border + EMPHASIZED_BORDER_THICKNESS)
      outlinePath.lineTo(view.x.toFloat() + width + 2f * border - EMPHASIZED_BORDER_THICKNESS,
                         view.y.toFloat() - EMPHASIZED_BORDER_OUTLINE_THICKNESS)
      if (width + 2f * border - EMPHASIZED_BORDER_THICKNESS > view.width) {
        outlinePath.lineTo(view.x.toFloat() + width + 2f * border - EMPHASIZED_BORDER_THICKNESS,
                           view.y.toFloat())
        outlinePath.lineTo(view.x.toFloat() + view.width.toFloat() + EMPHASIZED_BORDER_OUTLINE_THICKNESS,
                           view.y.toFloat())
      }
      g2.draw(outlinePath)
      g2.color = SELECTED_LINE_COLOR
      g2.fill(Rectangle2D.Float(view.x.toFloat() - EMPHASIZED_BORDER_THICKNESS / 2f,
                                view.y - height - border + EMPHASIZED_BORDER_THICKNESS / 2f,
                                width + 2f * border, height + border))
      g2.color = Color.WHITE
      g2.drawString(view.unqualifiedName, view.x + border, view.y - border)
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelChanged(old: ViewNode?, new: ViewNode?, structuralChange: Boolean) {
    model.refresh()
    repaint()
  }
}