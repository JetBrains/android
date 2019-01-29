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
import icons.StudioIcons
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(layoutInspector: LayoutInspector) : JPanel(BorderLayout()), Zoomable, DataProvider {

  enum class ViewMode(val icon: Icon) {
    FIXED(StudioIcons.LayoutEditor.Extras.ROOT_INLINE),
    X_ONLY(StudioIcons.DeviceConfiguration.SCREEN_WIDTH),
    XY(StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE)
  }

  var viewState = ViewMode.XY

  override var scale: Double = .5

  override val screenScalingFactor = 1f

  private var drawBorders = true

  private var model = DeviceViewPanelModel(layoutInspector.layoutInspectorModel)

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

  private var inspectorModel = layoutInspector.layoutInspectorModel

  init {
    for ((name, data) in arrayOf("Chrome" to chromeSampleData, "Videos" to videosSampleData, "Youtube" to youtubeSampleData)) {
      sampleDataSelector.addAction(object : AnAction(name) {
        override fun actionPerformed(e: AnActionEvent) {
          inspectorModel = data
          repaint()
        }
      })
    }

    layoutInspector.modelChangeListeners.add(::modelChanged)
    inspectorModel.selectionListeners.add(::selectionChanged)

    add(createToolbar(), BorderLayout.NORTH)

    val panel = object : JPanel() {
      override fun paint(g: Graphics) {
        super.paint(g)
        val g2d = g as? Graphics2D ?: return
        g2d.setRenderingHints(HQ_RENDERING_HINTS)
        g2d.translate(size.width / 2.0, size.height / 2.0)
        g2d.scale(scale, scale)
        model.hitRects.forEach { (rect, transform, view) ->
          drawView(g2d, view, rect, transform)
        }
      }
    }
    val listener = object : MouseAdapter() {
      private var x = 0
      private var y = 0

      override fun mousePressed(e: MouseEvent) {
        x = e.x
        y = e.y
      }

      override fun mouseDragged(e: MouseEvent) {
        if (viewState != ViewMode.FIXED) {
          model.rotateX((e.x - x) * 0.001)
          x = e.x
        }
        if (viewState == ViewMode.XY) {
          model.rotateY((e.y - y) * 0.001)
          y = e.y
        }

        refresh()
      }
    }
    panel.addMouseListener(listener)
    panel.addMouseMotionListener(listener)

    val borderPanel = JPanel(BorderLayout())
    borderPanel.add(panel, BorderLayout.CENTER)
    add(borderPanel, BorderLayout.CENTER)

    val mouseListener = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        inspectorModel.selection = model.findTopRect((e.x - panel.size.width / 2.0) / scale, (e.y - panel.size.height / 2.0) / scale)
        repaint()
      }
    }
    panel.addMouseListener(mouseListener)
    panel.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        refresh()
      }
    })
    refresh()
  }

  override fun zoom(type: ZoomType): Boolean {
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> scale = 0.5
      ZoomType.ACTUAL -> scale = 1.0
      ZoomType.IN -> scale += 0.1
      ZoomType.OUT -> scale -= 0.1
    }
    refresh()
    return true
  }

  override fun canZoomIn() = true

  override fun canZoomOut() = true

  override fun canZoomToFit() = true

  private fun drawView(g: Graphics,
                       view: InspectorView,
                       rect: Shape,
                       transform: AffineTransform) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    if (drawBorders) {
      if (view == inspectorModel.selection) {
        g2.color = Color.RED
        g2.stroke = BasicStroke(3f)
      }
      else {
        g2.color = Color.BLUE
        g2.stroke = BasicStroke(1f)
      }
      g2.draw(rect)
    }

    g2.transform = g2.transform.apply { concatenate(transform) }

    val bufferedImage = view.image
    if (bufferedImage != null) {
      val composite = g2.composite
      if (inspectorModel.selection != null && view != inspectorModel.selection) {
        g2.composite = AlphaComposite.SrcOver.derive(0.6f)
      }
      g2.drawImage(bufferedImage, view.x, view.y, null)
      g2.composite = composite
    }
    if (drawBorders && view == inspectorModel.selection) {
      g2.color = Color.BLACK
      g2.font = g2.font.deriveFont(20f)
      g2.drawString(view.type, view.x + 5, view.y + 25)
    }
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
    rightGroup.add(object: AnAction("reset") {
      override fun actionPerformed(e: AnActionEvent) {
        when (viewState) {
          ViewMode.XY -> {
            model.resetRotation()
            viewState = ViewMode.FIXED
          }
          ViewMode.FIXED -> viewState = ViewMode.X_ONLY
          ViewMode.X_ONLY -> viewState = ViewMode.XY
        }
        refresh()
      }

      override fun update(e: AnActionEvent) {
        e.presentation.icon = viewState.icon
      }
    })
    rightGroup.add(ZoomOutAction)
    rightGroup.add(ZoomLabelAction)
    rightGroup.add(ZoomInAction)
    rightGroup.add(ZoomToFitAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorRight", rightGroup, true)
    toolbar.setTargetComponent(this)
    panel.add(toolbar.component, BorderLayout.EAST)
    return panel
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    old.selectionListeners.remove(this::selectionChanged)
    new.selectionListeners.add(this::selectionChanged)
    model = DeviceViewPanelModel(new)
    repaint()
  }

  private fun selectionChanged(old: InspectorView?, new: InspectorView?) {
    repaint()
  }

  private fun refresh() {
    model.refresh()
    repaint()
  }
}
