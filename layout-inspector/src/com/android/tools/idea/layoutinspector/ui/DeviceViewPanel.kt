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
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

private const val LAYER_SPACING = 150

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(private val layoutInspector: LayoutInspector) : JPanel(BorderLayout()), Zoomable, DataProvider {

  enum class ViewMode(val icon: Icon) {
    FIXED(StudioIcons.LayoutEditor.Extras.ROOT_INLINE),
    X_ONLY(StudioIcons.DeviceConfiguration.SCREEN_WIDTH),
    XY(StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE)
  }

  var viewState = ViewMode.XY

  var rootDimension: Dimension = Dimension()

  override var scale: Double = .5

  override val screenScalingFactor = 1f

  override fun zoom(type: ZoomType): Boolean {
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> scale = 0.5
      ZoomType.ACTUAL -> scale = 1.0
      ZoomType.IN -> scale += 0.1
      ZoomType.OUT -> scale -= 0.1
    }
    rebuildRects()
    return true
  }

  override fun canZoomIn() = true

  override fun canZoomOut() = true

  override fun canZoomToFit() = true

  private var drawBorders = true

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

  private var xOff = 0.0
  private var yOff = 0.0
  private val hitRects = ArrayList<Triple<Shape, AffineTransform, InspectorView>>()

  private val mouseListener = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      layoutInspector.layoutInspectorModel.selection = hitRects.findLast {
        it.first.contains(e.x.toDouble(), e.y.toDouble())
      }?.third
      repaint()
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

    layoutInspector.modelChangeListeners.add(::modelChanged)
    layoutInspector.layoutInspectorModel.selectionListeners.add(::selectionChanged)

    add(createToolbar(), BorderLayout.NORTH)

    val panel = object : JPanel() {
      override fun paint(g: Graphics) {
        super.paint(g)
        (g as? Graphics2D)?.setRenderingHints(HQ_RENDERING_HINTS)
        hitRects.forEach { (rect, transform, view) ->
          drawView(g, view, rect, transform)
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
          xOff += (e.x - x) * 0.001
          xOff = max(-1.0, min(1.0, xOff))
          x = e.x
        }
        if (viewState == ViewMode.XY) {
          yOff += (e.y - y) * 0.001
          yOff = max(-1.0, min(1.0, yOff))
          y = e.y
        }

        rebuildRects()
      }
    }
    panel.addMouseListener(listener)
    panel.addMouseMotionListener(listener)

    val borderPanel = JPanel(BorderLayout())
    borderPanel.add(panel, BorderLayout.CENTER)
    add(borderPanel, BorderLayout.CENTER)
    panel.addMouseListener(mouseListener)
    panel.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        rebuildRects()
      }
    })
    rebuildRects()
    rootDimension = Dimension(layoutInspector.layoutInspectorModel.root.width, layoutInspector.layoutInspectorModel.root.height)
  }

  private fun drawView(g: Graphics,
                       view: InspectorView,
                       rect: Shape,
                       transform: AffineTransform) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    if (drawBorders) {
      if (view == layoutInspector.layoutInspectorModel.selection) {
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
      if (layoutInspector.layoutInspectorModel.selection != null && view != layoutInspector.layoutInspectorModel.selection) {
        g2.composite = AlphaComposite.SrcOver.derive(0.6f)
      }
      g2.drawImage(bufferedImage, view.x, view.y, null)
      g2.composite = composite
    }
    if (drawBorders && view == layoutInspector.layoutInspectorModel.selection) {
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
            xOff = 0.0
            yOff = 0.0
            viewState = ViewMode.FIXED
          }
          ViewMode.FIXED -> viewState = ViewMode.X_ONLY
          ViewMode.X_ONLY -> viewState = ViewMode.XY
        }
        rebuildRects()
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

  private fun rebuildRects() {
    hitRects.clear()
    val transform = AffineTransform()
    transform.translate(size.width / 2.0, size.height / 2.0)
    transform.scale(scale, scale)
    transform.translate(-layoutInspector.layoutInspectorModel.root.width / 2.0, -layoutInspector.layoutInspectorModel.root.height / 2.0)

    val magnitude = min(1.0, hypot(xOff, yOff))
    val angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

    transform.translate(rootDimension.width / 2.0, rootDimension.height / 2.0)
    transform.rotate(angle)
    val maxDepth = findMaxDepth(layoutInspector.layoutInspectorModel.root)
    rebuildOneRect(transform, magnitude, 0, maxDepth, angle, layoutInspector.layoutInspectorModel.root)
    repaint()
  }

  private fun findMaxDepth(view: InspectorView): Int {
    return 1 + (view.children.map { findMaxDepth(it) }.max() ?: 0)
  }

  private fun rebuildOneRect(transform: AffineTransform,
                             magnitude: Double,
                             depth: Int,
                             maxDepth: Int,
                             angle: Double,
                             view: InspectorView) {
    val viewTransform = AffineTransform(transform)

    val sign = if (xOff < 0) -1 else 1
    viewTransform.translate(magnitude * (depth - maxDepth / 2) * LAYER_SPACING * sign, 0.0)
    viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
    viewTransform.rotate(-angle)
    viewTransform.translate(-rootDimension.width / 2.0, -rootDimension.height / 2.0)

    val rect = viewTransform.createTransformedShape(Rectangle(view.x, view.y, view.width, view.height))
    hitRects.add(Triple(rect, viewTransform, view))
    view.children.forEach { rebuildOneRect(transform, magnitude, depth + 1, maxDepth, angle, it) }
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    old.selectionListeners.remove(this::selectionChanged)
    new.selectionListeners.add(this::selectionChanged)
    rootDimension = Dimension(new.root.width, new.root.height)
    rebuildRects()
  }

  private fun selectionChanged(old: InspectorView?, new: InspectorView?) {
    repaint()
  }
}
