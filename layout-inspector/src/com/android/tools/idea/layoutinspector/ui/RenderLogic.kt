/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.getFoldStroke
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D

private val HQ_RENDERING_HINTS = mapOf(
  RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
  RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
  RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_ON,
  RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
  RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
  RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
)

/**
 * Class defining the logic that renders the Layout Inspector device-related components. Eg: images, borders etc.
 */
class RenderLogic(private val renderModel: RenderModel, private val renderSettings: RenderSettings) {
  private val inspectorModel = renderModel.model

  /**
   * Render Views images from the device.
   */
  fun renderImages(g: Graphics2D) {
    renderModel.hitRects.forEach { renderImages(g, it) }
  }

  /**
   * Render Views borders from the device.
   */
  fun renderBorders(g: Graphics2D, component: Component, foregroundColor: Color) {
    renderModel.hitRects.forEach { viewDrawInfo -> renderBorders(g, viewDrawInfo, component, foregroundColor) }
  }

  /**
   * Render an overlay image
   */
  fun renderOverlay(g: Graphics2D) {
    if (renderModel.overlay == null) {
      return
    }

    g.composite = AlphaComposite.SrcOver.derive(renderModel.overlayAlpha)
    val bounds = renderModel.hitRects[0].bounds.bounds
    g.drawImage(renderModel.overlay, bounds.x, bounds.y, bounds.width, bounds.height, null)
  }

  private fun renderImages(g: Graphics, drawInfo: ViewDrawInfo) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }
    drawInfo.node.paint(g2, inspectorModel)
  }

  private fun renderBorders(g: Graphics2D, drawInfo: ViewDrawInfo, component: Component, foregroundColor: Color) {
    val hoveredNode = inspectorModel.hoveredNode
    val drawView = drawInfo.node
    val view = drawView.findFilteredOwner(renderModel.treeSettings)
    val selection = inspectorModel.selection

    val g2 = g.create() as Graphics2D
    g2.setRenderingHints(HQ_RENDERING_HINTS)
    g2.transform = g2.transform.apply { concatenate(drawInfo.transform) }

    if (!drawInfo.isCollapsed &&
        (renderSettings.drawBorders || renderSettings.drawUntransformedBounds || view == selection || view == hoveredNode ||
         (renderModel.treeSettings.showRecompositions &&
          (view as? ComposeViewNode)?.recompositions?.hasHighlight == true &&
          inspectorModel.maxHighlight != 0f)
        )
    ) {
      drawView.paintBorder(g2, view == selection, view == hoveredNode, inspectorModel, renderSettings, renderModel.treeSettings)
    }
    // the fold has to be drawn over the View that is select/hovered.
    // This matters only in 3D, where users want to know where the fold is relative to each View.
    // Since the Views are rotated it is more difficult to understand where they are relative to the fold.
    if (renderSettings.drawFold && renderModel.hitRects.isNotEmpty() && (
        // nothing is selected or hovered: draw on the root
        (renderModel.hoveredDrawInfo == null && inspectorModel.selection == null && drawInfo == renderModel.hitRects.first()) ||
        // We're hovering over this node
        renderModel.hoveredDrawInfo == drawInfo ||
        // We're not hovering but there is a selection. If the selected ViewNode corresponds to multiple DrawViewNodes (that is, both
        // a structural DrawViewChild and one or more image-containing DrawViewImage), only draw on the bottom one (the DrawViewChild).
        (renderModel.hoveredDrawInfo == null && view != null && inspectorModel.selection == view && drawView is DrawViewChild))) {
      renderFold(g2, component, foregroundColor)
    }
  }

  /**
   * Render the fold from a foldable device.
   */
  private fun renderFold(g2: Graphics2D, component: Component, foregroundColor: Color) {
    g2.color = Color(255, 0, 255)
    g2.stroke = getFoldStroke(renderSettings.scaleFraction)
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
    labelGraphics.color = foregroundColor
    icon.paintIcon(component, labelGraphics, labelPosition.x, labelPosition.y)
    labelGraphics.drawString(angleText, labelPosition.x + icon.iconWidth + iconTextGap,
                             labelPosition.y + labelGraphics.fontMetrics.maxAscent)
  }
}