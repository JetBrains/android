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

import com.android.tools.adtui.Pannable
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.getDrawNodeLabelHeight
import com.android.tools.idea.layoutinspector.model.getEmphasizedBorderOutlineThickness
import com.android.tools.idea.layoutinspector.model.getLabelFontSize
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import javax.swing.JComponent

private const val MARGIN = 50

private const val FRAMES_BEFORE_RESET_TO_BITMAP = 3

// We use a generic DropDownAction container because actions can be [SelectDeviceAction] or [SelectProcessAction].
data class DropDownActionWithButton(val dropDownAction: DropDownAction, val getButton: () -> JComponent?)

class DeviceViewContentPanel(
  val inspectorModel: InspectorModel,
  val deviceModel: DeviceModel?,
  val treeSettings: TreeSettings,
  val renderSettings: RenderSettings,
  val currentClient: () -> InspectorClient?,
  val pannable: Pannable,
  @VisibleForTesting val selectTargetAction: DropDownActionWithButton?,
  disposableParent: Disposable,
  val isLoading: () -> Boolean,
  val isCurrentForegroundProcessDebuggable: () -> Boolean,
  val hasForegroundProcess: () -> Boolean
) : AdtPrimaryPanel() {

  val renderModel = RenderModel(inspectorModel, treeSettings, currentClient)
  val renderLogic = RenderLogic(renderModel, renderSettings)

  @get:VisibleForTesting
  val showEmptyText get() = !renderModel.isActive && !isLoading() && deviceModel?.selectedDevice == null

  @get:VisibleForTesting
  val showProcessNotDebuggableText get() = !renderModel.isActive &&
                                           !isLoading() &&
                                           deviceModel?.selectedDevice != null &&
                                           hasForegroundProcess() &&
                                           !isCurrentForegroundProcessDebuggable()

  @get:VisibleForTesting
  val showNavigateToDebuggableProcess get() = !renderModel.isActive &&
                                              !isLoading() &&
                                              deviceModel?.selectedDevice != null &&
                                              !hasForegroundProcess()

  val rootLocation: Point?
    get() {
      val modelCoordinates =  renderModel.hitRects.firstOrNull()?.bounds?.bounds?.location ?: return null
      val panelCoordinates = toPanelCoordinates(modelCoordinates.x, modelCoordinates.y)
      return Point(panelCoordinates.x.toInt(), panelCoordinates.y.toInt())
    }

  /**
   * Transform to the center of the panel and apply scale
   */
  private val deviceViewContentPanelTransform: AffineTransform
    get() {
      return AffineTransform().apply {
        translate(size.width / 2.0, size.height / 2.0)
        scale(renderSettings.scaleFraction, renderSettings.scaleFraction)
      }
    }

  private val emptyText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = showEmptyText
  }

  private val processNotDebuggableText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = showProcessNotDebuggableText
  }

  private val navigateToDebuggableProcessText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = showNavigateToDebuggableProcess
  }

  init {
    processNotDebuggableText.appendLine(LayoutInspectorBundle.message("application.not.inspectable"))
    processNotDebuggableText.appendLine(LayoutInspectorBundle.message("navigate.to.debuggable.application"))

    navigateToDebuggableProcessText.appendLine(LayoutInspectorBundle.message("navigate.to.debuggable.application"))

    selectTargetAction?.let { selectTargetAction ->
      emptyText.appendLine(LayoutInspectorBundle.message("no.process.connected"))

      emptyText.appendLine("Deploy your app or ")
      @Suppress("DialogTitleCapitalization")
      val text = if (LayoutInspectorSettings.getInstance().autoConnectEnabled) {
        "select a device"
      }
      else {
        "select a process"
      }

      emptyText.appendText(text, SimpleTextAttributes.LINK_ATTRIBUTES) {
        val button = selectTargetAction.getButton()
        val dataContext = DataManager.getInstance().getDataContext(button)
        selectTargetAction.dropDownAction.templatePresentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, button)
        val event = AnActionEvent.createFromDataContext(
          ActionPlaces.TOOLWINDOW_CONTENT,
          selectTargetAction.dropDownAction.templatePresentation,
          dataContext
        )
        selectTargetAction.dropDownAction.actionPerformed(event)
      }
      @Suppress("DialogTitleCapitalization")
      emptyText.appendText(" to begin inspection.")

      emptyText.appendLine("")
      emptyText.appendLine(AllIcons.General.ContextHelp, "Using the layout inspector", SimpleTextAttributes.LINK_ATTRIBUTES) {
        BrowserUtil.browse("https://developer.android.com/studio/debug/layout-inspector")
      }
    }
    isOpaque = true
    inspectorModel.selectionListeners.add { _, _, origin -> autoScrollAndRepaint(origin) }
    inspectorModel.hoverListeners.add { _, _ -> repaint() }
    inspectorModel.connectionListeners.add { renderModel.resetRotation() }
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
        if (renderModel.overlay != null || currentClient()?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) != true) {
          // can't rotate
          return
        }
        if (renderModel.isRotated) {
          val xRotation = (e.x - x) * 0.001
          val yRotation = (e.y - y) * 0.001
          x = e.x
          y = e.y
          if (xRotation != 0.0 || yRotation != 0.0) {
            renderModel.rotate(xRotation, yRotation)
          }
          repaint()
        }
        else if ((e.x - x) + (e.y - y) > 50) {
          // Drag when rotation is disabled. Show tooltip.
          val dataContext = DataManager.getInstance().getDataContext(this@DeviceViewContentPanel)
          dataContext.getData(TOGGLE_3D_ACTION_BUTTON_KEY)?.let { toggle3dButton ->
            GotItTooltip("LayoutInspector.RotateViewTooltip", "Click to toggle 3D mode", disposableParent)
              .withShowCount(FRAMES_BEFORE_RESET_TO_BITMAP)
              .withPosition(Balloon.Position.atLeft)
              .show(toggle3dButton, GotItTooltip.LEFT_MIDDLE)
          }
        }
      }

      override fun mouseClicked(e: MouseEvent) {
        if (e.isConsumed) return
        val modelCoordinates = toModelCoordinates(e.x, e.y)
        val view = renderModel.findTopViewAt(modelCoordinates.x, modelCoordinates.y)
        inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
        currentClient()?.stats?.selectionMadeFromImage(view)
      }

      override fun mouseMoved(e: MouseEvent) {
        if (e.isConsumed) return
        val modelCoordinates = toModelCoordinates(e.x, e.y)
        renderModel.hoveredDrawInfo = renderModel.findDrawInfoAt(modelCoordinates.x, modelCoordinates.y).firstOrNull()
        inspectorModel.hoveredNode = renderModel.hoveredDrawInfo?.node?.findFilteredOwner(treeSettings)
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (!pannable.isPanning) {
          val modelCoordinates = toModelCoordinates(x, y)
          val views = renderModel.findViewsAt(modelCoordinates.x, modelCoordinates.y)
          showViewContextMenu(views.toList(), inspectorModel, this@DeviceViewContentPanel, x, y)
        }
      }
    })

    renderSettings.modificationListeners.add { repaint() }
    // If we get three consecutive pictures where SKPs aren't needed, reset to bitmap.
    var toResetCount = 0
    inspectorModel.modificationListeners.add { _, _, _ ->
      // SKP is needed if the view is rotated or if anything is hidden. We have to check on each update, since previously-hidden nodes
      // may have been removed.
      val currentClient = currentClient()
      if ((inspectorModel.pictureType == AndroidWindow.ImageType.SKP ||
           inspectorModel.pictureType == AndroidWindow.ImageType.SKP_PENDING) &&
          currentClient?.isCapturing == true &&
          !renderModel.isRotated && !inspectorModel.hasHiddenNodes()) {
        // We know for sure there's not a hidden descendant now, so update the field in case it was out of date.
        if (toResetCount++ > FRAMES_BEFORE_RESET_TO_BITMAP) {
          toResetCount = 0
          // Be sure to reset the scale as well, since if we were previously paused the scale will be set to 1.
          currentClient.updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED, renderSettings.scaleFraction.toFloat())
        }
      }
      else {
        // SKP was needed
        toResetCount = 0
      }
    }
    renderModel.modificationListeners.add {
      revalidate()
      repaint()
    }
    ActionManager.getInstance()?.getAction(IdeActions.ACTION_GOTO_DECLARATION)?.shortcutSet
      ?.let { GotoDeclarationAction.registerCustomShortcutSet(it, this, disposableParent) }
  }

  /**
   * Transform panel coordinates to model coordinates.
   */
  private fun toModelCoordinates(x: Int, y: Int): Point2D {
    val originalPoint2D = Point2D.Double(x.toDouble(), y.toDouble())
    val transformedPoint2D = Point2D.Double()
    deviceViewContentPanelTransform.inverseTransform(originalPoint2D, transformedPoint2D)
    return transformedPoint2D
  }

  private fun toPanelCoordinates(x: Int, y: Int): Point2D {
    val originalPoint2D = Point2D.Double(x.toDouble(), y.toDouble())
    val transformedPoint2D = Point2D.Double()
    deviceViewContentPanelTransform.transform(originalPoint2D, transformedPoint2D)
    return transformedPoint2D
  }

  private fun toPanelCoordinates(rectangle: Rectangle): Shape {
    return deviceViewContentPanelTransform.createTransformedShape(rectangle)
  }

  override fun paint(g: Graphics?) {
    val g2d = g as? Graphics2D ?: return
    g2d.color = primaryPanelBackground
    g2d.fillRect(0, 0, width, height)

    emptyText.paint(this, g)
    processNotDebuggableText.paint(this, g)
    navigateToDebuggableProcessText.paint(this, g)

    g2d.transform = g2d.transform.apply { concatenate(deviceViewContentPanelTransform) }

    renderLogic.renderImages(g2d)
    renderLogic.renderBorders(g2d, this, foreground)
    renderLogic.renderOverlay(g2d)
  }

  /**
   * Change the panel size with the size of what is being rendered.
   * This makes sure that when the render is too big to be entirely visible, the panel expands and scrollbars are shown.
   */
  override fun getPreferredSize(): Dimension {
    val (desiredWidth, desiredHeight) = when {
      inspectorModel.isEmpty -> Pair(0, 0)
      // If rotated, give twice the needed size, so we have room to move the view around a little. Otherwise things can jump around
      // when the number of layers changes and the canvas size adjusts to smaller than the viewport size.
      renderModel.isRotated -> Pair(renderModel.maxWidth * 2, renderModel.maxHeight * 2)
      else -> inspectorModel.root.transitiveBounds.run { Pair(width, height) }
    }
    return Dimension((desiredWidth * renderSettings.scaleFraction).toInt() + JBUIScale.scale(MARGIN) * 2,
                     (desiredHeight * renderSettings.scaleFraction).toInt() + JBUIScale.scale(MARGIN) * 2)
  }

  private fun autoScrollAndRepaint(origin: SelectionOrigin) {
    val selection = inspectorModel.selection
    if (origin != SelectionOrigin.INTERNAL && selection != null) {
      val hits = renderModel.hitRects.filter { it.node.findFilteredOwner(treeSettings) == selection }
      val bounds = Rectangle()
      hits.forEach { if (bounds.isEmpty) bounds.bounds = it.bounds.bounds else bounds.add(it.bounds.bounds) }
      if (!bounds.isEmpty) {
        val font = StartupUiUtil.getLabelFont().deriveFont(getLabelFontSize(renderSettings.scaleFraction))
        val fontMetrics = getFontMetrics(font)
        val textWidth = fontMetrics.stringWidth(selection.unqualifiedName)
        val labelHeight = getDrawNodeLabelHeight(renderSettings.scaleFraction).toInt()
        val borderSize = getEmphasizedBorderOutlineThickness(renderSettings.scaleFraction).toInt() / 2
        bounds.width = kotlin.math.max(bounds.width, textWidth)
        bounds.x -= borderSize
        bounds.y -= borderSize + labelHeight
        bounds.width += borderSize * 2
        bounds.height += borderSize * 2 + labelHeight

        val panelBounds = toPanelCoordinates(bounds)
        bounds.x = panelBounds.bounds.x
        bounds.y = panelBounds.bounds.y
        bounds.width = panelBounds.bounds.width
        bounds.height = panelBounds.bounds.height

        scrollRectToVisible(bounds)
      }
    }
    repaint()
  }
}
