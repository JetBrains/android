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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.getDrawNodeLabelHeight
import com.android.tools.idea.layoutinspector.model.getEmphasizedBorderOutlineThickness
import com.android.tools.idea.layoutinspector.model.getFoldStroke
import com.android.tools.idea.layoutinspector.model.getLabelFontSize
import com.android.tools.idea.layoutinspector.pipeline.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
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
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.StatusText
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
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
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import javax.swing.JComponent

private const val MARGIN = 50

private const val FRAMES_BEFORE_RESET_TO_BITMAP = 3

private val HQ_RENDERING_HINTS = mapOf(
  RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
  RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
  RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_ON,
  RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
  RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
  RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
)

// We use a generic DropDownAction container because actions can be [SelectDeviceAction] or [SelectProcessAction].
data class DropDownActionWithButton(val dropDownAction: DropDownAction, val getButton: () -> JComponent?)

class DeviceViewContentPanel(
  val inspectorModel: InspectorModel,
  val deviceModel: DeviceModel?,
  val treeSettings: TreeSettings,
  val viewSettings: DeviceViewSettings,
  val currentClient: () -> InspectorClient?,
  val pannable: Pannable,
  @VisibleForTesting val selectTargetAction: DropDownActionWithButton?,
  disposableParent: Disposable
) : AdtPrimaryPanel() {

  var showEmptyText = true
  var showProcessNotDebuggableText = false

  val model = DeviceViewPanelModel(inspectorModel, treeSettings, currentClient)

  val rootLocation: Point?
    get() {
      val modelLocation = model.hitRects.firstOrNull()?.bounds?.bounds?.location ?: return null
      return Point((modelLocation.x * viewSettings.scaleFraction).toInt() + (size.width / 2),
                   (modelLocation.y * viewSettings.scaleFraction).toInt() + (size.height / 2))
    }

  /**
   * Transform to the center of the panel and apply scale
   */
  private val deviceViewContentPanelTransform: AffineTransform
    get() {
      return AffineTransform().apply {
        translate(size.width / 2.0, size.height / 2.0)
        scale(viewSettings.scaleFraction, viewSettings.scaleFraction)
      }
    }

  private val emptyText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = !model.isActive && showEmptyText && deviceModel?.selectedDevice == null
  }

  private val processNotDebuggableText: StatusText = object : StatusText(this) {
    override fun isStatusVisible() = !model.isActive && showProcessNotDebuggableText && deviceModel?.selectedDevice != null
  }

  init {
    processNotDebuggableText.appendLine("Application not inspectable.")
    processNotDebuggableText.appendLine("Switch to a debuggable application on your device to inspect.")

    selectTargetAction?.let { selectTargetAction ->
      emptyText.appendLine("No process connected")

      emptyText.appendLine("Deploy your app or ")

      @Suppress("DialogTitleCapitalization")
      val text = if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()) {
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
        if (model.overlay != null || currentClient()?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) != true) {
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
          dataContext.getData(TOGGLE_3D_ACTION_BUTTON_KEY)?.let { toggle3dButton ->
            GotItTooltip("LayoutInspector.RotateViewTooltip", "Click to toggle 3D mode", disposableParent)
              .withShowCount(FRAMES_BEFORE_RESET_TO_BITMAP)
              .withPosition(Balloon.Position.atLeft)
              .show(toggle3dButton, GotItTooltip.LEFT_MIDDLE)
          }
        }
      }

      private fun nodeAtPoint(e: MouseEvent) = model.findTopViewAt((e.x - size.width / 2.0) / viewSettings.scaleFraction,
                                                                 (e.y - size.height / 2.0) / viewSettings.scaleFraction)

      override fun mouseClicked(e: MouseEvent) {
        if (e.isConsumed) return
        val view = nodeAtPoint(e)
        inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
        currentClient()?.stats?.selectionMadeFromImage(view)
      }

      override fun mouseMoved(e: MouseEvent) {
        if (e.isConsumed) return
        val modelCoordinates = toModelCoordinates(e.x, e.y)
        model.hoveredDrawInfo = model.findDrawInfoAt(modelCoordinates.x, modelCoordinates.y).firstOrNull()
        inspectorModel.hoveredNode = model.hoveredDrawInfo?.node?.findFilteredOwner(treeSettings)
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (!pannable.isPanning) {
          val modelCoordinates = toModelCoordinates(x, y)
          val views = model.findViewsAt(modelCoordinates.x, modelCoordinates.y)
          showViewContextMenu(views.toList(), inspectorModel, this@DeviceViewContentPanel, x, y)
        }
      }
    })

    viewSettings.modificationListeners.add { repaint() }
    // If we get three consecutive pictures where SKPs aren't needed, reset to bitmap.
    var toResetCount = 0
    inspectorModel.modificationListeners.add { _, _, _ ->
      // SKP is needed if the view is rotated or if anything is hidden. We have to check on each update, since previously-hidden nodes
      // may have been removed.
      val currentClient = currentClient()
      if ((inspectorModel.pictureType == AndroidWindow.ImageType.SKP ||
           inspectorModel.pictureType == AndroidWindow.ImageType.SKP_PENDING) &&
          currentClient?.isCapturing == true &&
          !model.isRotated && !inspectorModel.hasHiddenNodes()) {
        // We know for sure there's not a hidden descendant now, so update the field in case it was out of date.
        if (toResetCount++ > FRAMES_BEFORE_RESET_TO_BITMAP) {
          toResetCount = 0
          // Be sure to reset the scale as well, since if we were previously paused the scale will be set to 1.
          currentClient.updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED, viewSettings.scaleFraction.toFloat())
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

  override fun paint(g: Graphics?) {
    val g2d = g as? Graphics2D ?: return
    g2d.setRenderingHints(HQ_RENDERING_HINTS)
    g2d.color = primaryPanelBackground
    g2d.fillRect(0, 0, width, height)
    emptyText.paint(this, g)
    processNotDebuggableText.paint(this, g)

    g2d.transform = g2d.transform.apply { concatenate(deviceViewContentPanelTransform) }

    model.hitRects.forEach { drawImages(g2d, it) }
    model.hitRects.forEach { drawBorders(g2d, it) }

    if (model.overlay != null) {
      g2d.composite = AlphaComposite.SrcOver.derive(model.overlayAlpha)
      val bounds = model.hitRects[0].bounds.bounds
      g2d.drawImage(model.overlay, bounds.x, bounds.y, bounds.width, bounds.height, null)
    }
  }

  override fun getPreferredSize(): Dimension {
    val (desiredWidth, desiredHeight) = when {
      inspectorModel.isEmpty -> Pair(0, 0)
      // If rotated, give twice the needed size, so we have room to move the view around a little. Otherwise things can jump around
      // when the number of layers changes and the canvas size adjusts to smaller than the viewport size.
      model.isRotated -> Pair(model.maxWidth * 2, model.maxHeight * 2)
      else -> inspectorModel.root.transitiveBounds.run { Pair(width, height) }
    }
    return Dimension((desiredWidth * viewSettings.scaleFraction).toInt() + JBUIScale.scale(MARGIN) * 2,
                     (desiredHeight * viewSettings.scaleFraction).toInt() + JBUIScale.scale(MARGIN) * 2)
  }

  private fun autoScrollAndRepaint(origin: SelectionOrigin) {
    val selection = inspectorModel.selection
    if (origin != SelectionOrigin.INTERNAL && selection != null) {
      val hits = model.hitRects.filter { it.node.findFilteredOwner(treeSettings) == selection }
      val bounds = Rectangle()
      hits.forEach { if (bounds.isEmpty) bounds.bounds = it.bounds.bounds else bounds.add(it.bounds.bounds) }
      if (!bounds.isEmpty) {
        val font = StartupUiUtil.getLabelFont().deriveFont(getLabelFontSize(viewSettings.scaleFraction))
        val fontMetrics = getFontMetrics(font)
        val textWidth = fontMetrics.stringWidth(selection.unqualifiedName)
        val labelHeight = getDrawNodeLabelHeight(viewSettings.scaleFraction).toInt()
        val borderSize = getEmphasizedBorderOutlineThickness(viewSettings.scaleFraction).toInt() / 2
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
    val view = drawView.findFilteredOwner(treeSettings)
    val selection = inspectorModel.selection

    val g2 = g.create() as Graphics2D
    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }

    if (!drawInfo.isCollapsed &&
        (viewSettings.drawBorders || viewSettings.drawUntransformedBounds || view == selection || view == hoveredNode ||
         (treeSettings.showRecompositions &&
          (view as? ComposeViewNode)?.recompositions?.hasHighlight == true &&
          inspectorModel.maxHighlight != 0f)
        )
    ) {
      drawView.paintBorder(g2, view == selection, view == hoveredNode, inspectorModel, viewSettings, treeSettings)
    }
    if (viewSettings.drawFold && model.hitRects.isNotEmpty() && (
        // nothing is selected or hovered: draw on the root
        (model.hoveredDrawInfo == null && inspectorModel.selection == null && drawInfo == model.hitRects.first()) ||
        // We're hovering over this node
        model.hoveredDrawInfo == drawInfo ||
        // We're not hovering but there is a selection. If the selected ViewNode corresponds to multiple DrawViewNodes (that is, both
        // a structural DrawViewChild and one or more image-containing DrawViewImage), only draw on the bottom one (the DrawViewChild).
        (model.hoveredDrawInfo == null && view != null && inspectorModel.selection == view && drawView is DrawViewChild))) {
      drawFold(g2)
    }
  }

  private fun drawFold(g2: Graphics2D) {
    g2.color = Color(255, 0, 255)
    g2.stroke = getFoldStroke(viewSettings.scaleFraction)
    val foldInfo = inspectorModel.foldInfo ?: return
    val maxWidth = inspectorModel.windows.values.map { it.width }.maxOrNull() ?: 0
    val maxHeight = inspectorModel.windows.values.map { it.height }.maxOrNull() ?: 0

    val startX: Float
    val startY: Float
    val endX: Float
    val endY: Float

    val angleText = (if (foldInfo.angle == null) "" else foldInfo.angle?.toString() + "°") + " " + foldInfo.posture
    val labelPosition = Point()
    val icon = StudioIcons.LayoutInspector.DEGREE
    // Note this could be AdtUiUtils.DEFAULT_FONT, but since that's a static if it gets initialized during a test that overrides
    // ui defaults it can end up as something unexpected.
    g2.font = JBUI.Fonts.label(10f)
    val labelGraphics = (g2.create() as Graphics2D).apply { transform = AffineTransform() }
    val iconTextGap = JBUIScale.scale(4)
    val labelLineGap = JBUIScale.scale(7)
    val lineExtensionLength = JBUIScale.scale(70f)

    when (foldInfo.orientation) {
      InspectorModel.FoldOrientation.HORIZONTAL -> {
        startX = -lineExtensionLength
        endX = maxWidth + lineExtensionLength
        startY = maxHeight / 2f
        endY = maxHeight / 2f
        val transformed = g2.transform.transform(Point2D.Float(startX, startY), null)
        labelPosition.x = transformed.x.toInt() - labelGraphics.fontMetrics.stringWidth(
          angleText) - icon.iconWidth - iconTextGap - labelLineGap
        labelPosition.y = transformed.y.toInt() - icon.iconHeight / 2
      }
      InspectorModel.FoldOrientation.VERTICAL -> {
        startX = maxWidth / 2f
        endX = maxWidth / 2f
        startY = -lineExtensionLength
        endY = maxHeight + lineExtensionLength
        val transformed = g2.transform.transform(Point2D.Float(startX, startY), null)
        labelPosition.x = transformed.x.toInt() - (labelGraphics.fontMetrics.stringWidth(angleText) + icon.iconWidth + iconTextGap) / 2
        labelPosition.y = transformed.y.toInt() - icon.iconHeight - labelLineGap
      }
    }
    g2.draw(Line2D.Float(startX, startY, endX, endY))
    labelGraphics.color = JBColor.white
    val labelBorder = JBUIScale.scale(3)
    labelGraphics.fillRoundRect(labelPosition.x - labelBorder, labelPosition.y - labelBorder,
                                labelGraphics.fontMetrics.stringWidth(angleText) + icon.iconWidth + iconTextGap + labelBorder * 2,
                                icon.iconHeight + labelBorder * 2, JBUIScale.scale(5), JBUIScale.scale(5))
    labelGraphics.color = foreground
    icon.paintIcon(this, labelGraphics, labelPosition.x, labelPosition.y)
    labelGraphics.drawString(angleText, labelPosition.x + icon.iconWidth + iconTextGap,
                             labelPosition.y + labelGraphics.fontMetrics.maxAscent)
  }

  private fun drawImages(g: Graphics, drawInfo: ViewDrawInfo) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }
    drawInfo.node.paint(g2, inspectorModel)
  }
}
