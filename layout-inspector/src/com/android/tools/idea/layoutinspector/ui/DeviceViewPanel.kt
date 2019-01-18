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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.sampledata.chromeSampleData
import com.android.tools.idea.layoutinspector.sampledata.videosSampleData
import com.android.tools.idea.layoutinspector.sampledata.youtubeSampleData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.LinkedList
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.cos
import kotlin.math.sin

private const val LAYER_SPACING = 150

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(private val layoutInspector: LayoutInspector) : JPanel(BorderLayout()), Zoomable, DataProvider {

  override var scale: Double = 1.0

  override val screenScalingFactor: Float
    get() = 1f

  override fun zoom(type: ZoomType): Boolean {
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> scale = 0.5
      ZoomType.ACTUAL -> scale = 1.0
      ZoomType.IN -> scale += 0.1
      ZoomType.OUT -> scale -= 0.1
    }
    repaint()
    return true
  }

  override fun canZoomIn() = true

  override fun canZoomOut() = true

  override fun canZoomToFit() = true

  private var drawBorders = false

  private val showBordersCheckBox = object : CheckboxAction("Show borders") {
    override fun isSelected(e: AnActionEvent): Boolean {
      return drawBorders
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      drawBorders = state
      repaint()
    }
  }
  private val sampleDataSelector = DropDownAction("Sample Data", "Sample Data", null)

  private val HQ_RENDERING_HINTS = mapOf(
    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
  )

  private var angle = 0.0
  private val hitRects = ArrayList<Pair<Rectangle, InspectorView>>()

  private val mouseListener = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      layoutInspector.layoutInspectorModel.selection = hitRects.findLast {
        it.first.contains(e.x / scale, e.y / scale)
      }?.second
    }
  }

  init {
    for ((name, data) in arrayOf("Chrome" to chromeSampleData, "Videos" to videosSampleData, "Youtube" to youtubeSampleData)) {
      sampleDataSelector.addAction(object : AnAction(name) {
        override fun actionPerformed(e: AnActionEvent) {
          layoutInspector.layoutInspectorModel = data
          repaint()
        }
      })
    }

    layoutInspector.modelChangeListeners.add(this::modelChanged)
    layoutInspector.layoutInspectorModel.selectionListeners.add(this::selectionChanged)

    addMouseWheelListener { e ->
      angle += e.preciseWheelRotation * 0.01
      rebuildRects()
      repaint()
    }

    add(createToolbar(), BorderLayout.NORTH)

    val panel = object : JPanel() {
      override fun paint(g: Graphics) {
        super.paint(g)
        (g as? Graphics2D)?.setRenderingHints(HQ_RENDERING_HINTS)
        (g as? Graphics2D)?.scale(scale, scale)
        draw(layoutInspector.layoutInspectorModel.root, g, 0)
      }
    }
    val borderPanel = JPanel(BorderLayout())
    borderPanel.add(panel, BorderLayout.CENTER)
    borderPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
    add(borderPanel, BorderLayout.CENTER)
    panel.addMouseListener(mouseListener)
    rebuildRects()
  }

  override fun getData(dataId: String): Any? {
    if (ZOOMABLE_KEY.`is`(dataId)) {
      return this
    }
    return null
  }

  private fun createToolbar(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    val leftGroup = DefaultActionGroup()
    leftGroup.add(sampleDataSelector)
    leftGroup.add(showBordersCheckBox)
    leftPanel.add(ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true).component,
                  BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)

    val rightGroup = DefaultActionGroup()
    rightGroup.add(ZoomOutAction)
    rightGroup.add(ZoomLabelAction)
    rightGroup.add(ZoomInAction)
    rightGroup.add(ZoomToFitAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorRight", rightGroup, true)
    toolbar.setTargetComponent(this)
    panel.add(toolbar.component, BorderLayout.EAST)
    return panel
  }

  private fun rebuildRects() {
    hitRects.clear()
    val queue = LinkedList<Pair<InspectorView, Int>>()
    queue.add(layoutInspector.layoutInspectorModel.root to 0)
    while (!queue.isEmpty()) {
      val item = queue.remove()
      val view = item.first
      val rect = Rectangle(((view.x * cos(angle)) + (sin(angle) * item.second) * LAYER_SPACING).toInt(),
                           (view.y + (sin(angle * 0.1) * item.second * LAYER_SPACING)).toInt(),
                           (view.width * cos(angle)).toInt(), view.height)
      hitRects.add(rect to view)
      queue.addAll(view.children.map { it to item.second + 1 })
    }
  }

  private fun draw(view: InspectorView, g: Graphics, depth: Int) {
    val g2 = g.create() as Graphics2D
    val bufferedImage = view.image

    g2.translate((sin(angle) * depth * LAYER_SPACING).toInt(), (sin(angle * 0.1) * depth * LAYER_SPACING).toInt())
    g2.scale(cos(angle), 1.0)
    if (bufferedImage != null) {
      val composite = g2.composite
      if (layoutInspector.layoutInspectorModel.selection != null && view != layoutInspector.layoutInspectorModel.selection) {
        g2.composite = AlphaComposite.SrcOver.derive(0.6f)
      }
      g2.drawImage(bufferedImage, view.x, view.y, null)
      g2.composite = composite
    }
    if (drawBorders) {
      if (view == layoutInspector.layoutInspectorModel.selection) {
        g2.color = Color.BLACK
        g2.font = g2.font.deriveFont(20f)
        g2.drawString(view.type, view.x + 5, view.y + 25)
        g2.color = Color.RED
        g2.stroke = BasicStroke(3f)
      }
      else {
        g2.color = Color.BLUE
        g2.stroke = BasicStroke(1f)
      }
      g2.drawRect(view.x, view.y, view.width, view.height)
    }
    view.children.forEach { draw(it, g, depth + 1) }
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    old.selectionListeners.remove(this::selectionChanged)
    new.selectionListeners.add(this::selectionChanged)
    rebuildRects()
    repaint()
  }

  private fun selectionChanged(old: InspectorView?, new: InspectorView?) {
    repaint()
  }
}
