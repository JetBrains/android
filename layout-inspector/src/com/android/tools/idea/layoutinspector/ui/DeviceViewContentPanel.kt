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
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DRAW_NODE_LABEL_HEIGHT
import com.android.tools.idea.layoutinspector.model.EMPHASIZED_BORDER_OUTLINE_THICKNESS
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.LABEL_FONT_SIZE
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI

private const val MARGIN = 50

private const val FRAMES_BEFORE_RESET_TO_BITMAP = 3

private val HQ_RENDERING_HINTS = mapOf(
  RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
  RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
  RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
  RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
)

class DeviceViewContentPanel(
  val inspectorModel: InspectorModel, val viewSettings: DeviceViewSettings, disposableParent: Disposable
) : AdtPrimaryPanel() {

  @VisibleForTesting
  lateinit var selectProcessAction: SelectProcessAction

  @VisibleForTesting
  var showEmptyText = true

  val model = DeviceViewPanelModel(inspectorModel) { LayoutInspector.get(this@DeviceViewContentPanel)?.currentClient }

  val rootLocation: Point?
    get() {
      val modelLocation = model.hitRects.firstOrNull()?.bounds?.bounds?.location ?: return null
      return Point((modelLocation.x * viewSettings.scaleFraction).toInt() + (size.width / 2),
                   (modelLocation.y * viewSettings.scaleFraction).toInt() + (size.height / 2))
    }

  private val emptyText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = !model.isActive && showEmptyText
  }

  init {
    emptyText.appendLine("No process connected")
    emptyText.appendLine("Select process", SimpleTextAttributes.LINK_ATTRIBUTES) {
      val button = selectProcessAction.button
      val dataContext = DataManager.getInstance().getDataContext(button)
      selectProcessAction.templatePresentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, button)
      val event = AnActionEvent.createFromDataContext(ActionPlaces.TOOLWINDOW_CONTENT, selectProcessAction.templatePresentation, dataContext)
      selectProcessAction.actionPerformed(event)
    }
    emptyText.appendLine("")
    emptyText.appendLine(AllIcons.General.ContextHelp, "Using the layout inspector", SimpleTextAttributes.LINK_ATTRIBUTES) {
      Desktop.getDesktop().browse(URI("https://developer.android.com/studio/debug/layout-inspector"))
    }
    isOpaque = true
    inspectorModel.selectionListeners.add { _, _, origin -> autoScrollAndRepaint(origin) }
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
        val client = LayoutInspector.get(this@DeviceViewContentPanel)?.currentClient
        if (model.overlay != null || client?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) != true) {
          // can't rotate
          return
        }
        if (model.isRotated) {
          val xRotation = (e.x - x) * 0.001
          val yRotation = (e.y - y) * 0.001
          x = e.x
          y = e.y
          if (xRotation != 0.0 || yRotation != 0.0) {
            model.rotate(xRotation, yRotation)
          }
          repaint()
        }
        else if ((e.x - x) + (e.y - y) > 50) {
          // Drag when rotation is disabled. Show tooltip.
          val dataContext = DataManager.getInstance().getDataContext(this@DeviceViewContentPanel)
          val toggle3dButton = dataContext.getData(TOGGLE_3D_ACTION_BUTTON_KEY)!!
          GotItTooltip("LayoutInspector.RotateViewTooltip", "Click to toggle 3D mode", disposableParent)
            .withShowCount(FRAMES_BEFORE_RESET_TO_BITMAP)
            .withPosition(Balloon.Position.atLeft)
            .show(toggle3dButton, GotItTooltip.LEFT_MIDDLE)
        }
      }

      private fun nodeAtPoint(e: MouseEvent) = model.findTopViewAt((e.x - size.width / 2.0) / viewSettings.scaleFraction,
                                                                 (e.y - size.height / 2.0) / viewSettings.scaleFraction)

      override fun mouseClicked(e: MouseEvent) {
        if (e.isConsumed) return
        val view = nodeAtPoint(e)
        inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
        inspectorModel.stats.selectionMadeFromImage(view)
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
    // If we get three consecutive pictures where SKPs aren't needed, reset to bitmap.
    var toResetCount = 0
    inspectorModel.modificationListeners.add { _, _, _ ->
      // SKP is needed if the view is rotated or if anything is hidden. We have to check on each update, since previously-hidden nodes
      // may have been removed.
      val client = LayoutInspector.get(this@DeviceViewContentPanel)?.currentClient
      if (inspectorModel.pictureType == AndroidWindow.ImageType.SKP &&
          client?.isCapturing == true &&
          !model.isRotated && !inspectorModel.hasHiddenNodes()) {
        // We know for sure there's not a hidden descendant now, so update the field in case it was out of date.
        if (toResetCount++ > FRAMES_BEFORE_RESET_TO_BITMAP) {
          toResetCount = 0
          // Be sure to reset the scale as well, since if we were previously paused the scale will be set to 1.
          client.updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED, viewSettings.scaleFraction.toFloat())
        }
      }
      else {
        // SKP was needed
        toResetCount = 0
      }
    }
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
    g2d.setRenderingHints(HQ_RENDERING_HINTS)
    g2d.color = primaryPanelBackground
    g2d.fillRect(0, 0, width, height)
    emptyText.paint(this, g)
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

  private fun autoScrollAndRepaint(origin: SelectionOrigin) {
    val selection = inspectorModel.selection
    if (origin != SelectionOrigin.INTERNAL && selection != null) {
      val hits = model.hitRects.filter { it.node.owner == selection }
      val bounds = Rectangle()
      hits.forEach { if (bounds.isEmpty) bounds.bounds = it.bounds.bounds else bounds.add(it.bounds.bounds) }
      if (!bounds.isEmpty) {
        val font = UIUtil.getLabelFont().deriveFont(JBUIScale.scale(LABEL_FONT_SIZE))
        val fontMetrics = getFontMetrics(font)
        val textWidth = fontMetrics.stringWidth(selection.unqualifiedName)
        val labelHeight = JBUIScale.scale(DRAW_NODE_LABEL_HEIGHT).toInt()
        val borderSize = EMPHASIZED_BORDER_OUTLINE_THICKNESS.toInt() / 2
        bounds.width = kotlin.math.max(bounds.width, textWidth)
        bounds.x -= borderSize
        bounds.y -= borderSize + labelHeight
        bounds.width += borderSize * 2
        bounds.height += borderSize * 2 + labelHeight
        bounds.x = (bounds.x * viewSettings.scaleFraction).toInt() + (size.width / 2)
        bounds.y = (bounds.y * viewSettings.scaleFraction).toInt() + (size.height / 2)
        bounds.width = (bounds.width * viewSettings.scaleFraction).toInt()
        bounds.height = (bounds.height * viewSettings.scaleFraction).toInt()
        scrollRectToVisible(bounds)
      }
    }
    repaint()
  }

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
