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
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

private const val MARGIN = 50

class DeviceViewContentPanel(layoutInspector: LayoutInspector, val viewSettings: DeviceViewSettings) : AdtPrimaryPanel() {

  private val inspectorModel = layoutInspector.layoutInspectorModel
  var model = DeviceViewPanelModel(inspectorModel)

  private val HQ_RENDERING_HINTS = mapOf(
    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
  )

  init {
    inspectorModel.modificationListeners.add(::modelChanged)
    inspectorModel.selectionListeners.add(::selectionChanged)
    val mouseListener = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        inspectorModel.selection = model.findTopRect((e.x - size.width / 2.0) / viewSettings.scaleFraction,
                                                     (e.y - size.height / 2.0) / viewSettings.scaleFraction)
        repaint()
      }
    }
    addMouseListener(mouseListener)
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        repaint()
      }
    })

    val listener = object : MouseAdapter() {
      private var x = 0
      private var y = 0

      override fun mousePressed(e: MouseEvent) {
        x = e.x
        y = e.y
      }

      override fun mouseDragged(e: MouseEvent) {
        var xRotation = 0.0
        var yRotation = 0.0
        if (viewSettings.viewMode != ViewMode.FIXED) {
          xRotation = (e.x - x) * 0.001
          x = e.x
        }
        if (viewSettings.viewMode == ViewMode.XY) {
          yRotation = (e.y - y) * 0.001
          y = e.y
        }
        if (xRotation != 0.0 || yRotation != 0.0) {
          model.rotate(xRotation, yRotation)
        }
        repaint()
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)

    viewSettings.modificationListeners.add {
      if (viewSettings.viewMode == ViewMode.FIXED) {
        model.resetRotation()
      }
      // no need to handle X_ONLY since we can only get there starting at FIXED, so rotation will already be 0
      repaint()
    }
  }

  override fun paint(g: Graphics?) {
    val g2d = g as? Graphics2D ?: return
    g2d.color = background
    g2d.fillRect(0, 0, width, height)
    g2d.setRenderingHints(HQ_RENDERING_HINTS)
    g2d.translate(size.width / 2.0, size.height / 2.0)
    g2d.scale(viewSettings.scaleFraction, viewSettings.scaleFraction)

    // ViewNode.imageBottom are images that the parents draw on before their
    // children. Therefore draw them in the given order (parent first).
    model.hitRects.forEach { drawView(g2d, it, it.node.imageBottom) }

    // ViewNode.imageTop are images that the parents draw on top of their
    // children. Therefore draw them in the reverse order (children first).
    model.hitRects.asReversed().forEach { drawView(g2d, it, it.node.imageTop) }
  }

  override fun getPreferredSize() =
    if (inspectorModel.root == null) Dimension(0, 0)
    else Dimension((model.maxWidth * viewSettings.scaleFraction + JBUI.scale(MARGIN)).toInt(),
                   (model.maxHeight * viewSettings.scaleFraction + JBUI.scale(MARGIN)).toInt())

  private fun drawView(g: Graphics,
                       drawInfo: ViewDrawInfo,
                       image: Image?) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    val selection = inspectorModel.selection
    val view = drawInfo.node
    if (viewSettings.drawBorders) {
      if (view == selection) {
        g2.color = JBColor.RED
        g2.stroke = BasicStroke(3f)
      }
      else {
        g2.color = JBColor.BLUE
        g2.stroke = BasicStroke(1f)
      }
      g2.draw(drawInfo.bounds)
    }

    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }

    if (image != null) {
      val composite = g2.composite
      if (selection != null && view != selection) {
        g2.composite = AlphaComposite.SrcOver.derive(0.6f)
      }
      g2.clip(drawInfo.clip)
      UIUtil.drawImage(g2, image, view.x, view.y, null)
      g2.composite = composite
    }
    if (viewSettings.drawBorders && view == selection) {
      g2.color = Color.BLACK
      g2.font = g2.font.deriveFont(20f)
      g2.drawString(view.unqualifiedName, view.x + 5, view.y + 25)
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(old: ViewNode?, new: ViewNode?) {
    repaint()
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelChanged(old: ViewNode?, new: ViewNode?, structuralChange: Boolean) {
    model.refresh()
    repaint()
  }
}