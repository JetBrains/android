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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.utils.HtmlBuilder
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

private val colorAttributeName = Color.gray
private val colorAttributeValue = Color(0x80, 0x17, 0x8B)

/**
 * The x and y shift between tooltips and mouse position. This gives a small gap between mouse and
 * the top-left corner of tooltips to have better visual effect.
 */
@SwingCoordinate private const val TOOLTIPS_MOUSE_GAP = 10

open class ComposeNavigationInteractionHandler(
  surface: DesignSurface<*>,
  private val base: InteractionHandler
) : InteractionHandler by base {

  private val inspector =
    ComposePreviewInspector(
      surface,
      { sceneView ->
        val info =
          sceneView.scene.root?.nlComponent?.viewInfo ?: return@ComposePreviewInspector emptyList()
        parseViewInfo(
          info,
          { it },
          Logger.getInstance(ComposeNavigationInteractionHandler::class.java)
        )
      },
      TooltipPopupCreator(surface)
    )

  override fun hoverWhenNoInteraction(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
    modifiersEx: Int
  ) {
    base.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx)
    inspector.inspect(mouseX, mouseY)
  }

  private inner class TooltipPopupCreator(private val surface: DesignSurface<*>) :
    (List<ComposeViewInfo>, Int, Int) -> Unit {
    private var currentViewInfo: ComposeViewInfo? = null
    private var currentTooltipPopup: JBPopup? = null

    override fun invoke(
      viewInfos: List<ComposeViewInfo>,
      @SwingCoordinate swingX: Int,
      @SwingCoordinate swingY: Int
    ) {
      if (viewInfos.isEmpty()) {
        currentViewInfo = null
        currentTooltipPopup?.cancel()
        currentTooltipPopup = null
        return
      }
      val viewInfo = viewInfos.last()

      if (viewInfo == currentViewInfo) {
        return
      }
      currentTooltipPopup?.cancel()
      currentTooltipPopup = null

      val interactionPane = surface.interactionPane

      val panel = createTooltipPanel(viewInfo)
      val popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
      val popup = popupBuilder.createPopup()

      panel.addMouseListener(
        object : MouseAdapter() {
          override fun mouseEntered(e: MouseEvent) {
            // The inspection tooltip is for showing information only, it should not reflect any
            // mouse event.
            // When moving mouse into the popup, remove(hide) the popup.
            popup.cancel()
            currentViewInfo = null
            currentTooltipPopup = null

            val paneLocation = interactionPane.locationOnScreen
            val mouseLocation = e.locationOnScreen
            // After the popup is gone, check if the new position needs to show the tooltips.
            val mouseXInPane = mouseLocation.x - paneLocation.x
            val mouseYInPane = mouseLocation.y - paneLocation.y
            inspector.inspect(mouseXInPane, mouseYInPane)
            panel.removeMouseListener(this)
          }
        }
      )

      currentViewInfo = viewInfo
      currentTooltipPopup = popup
      // TODO: Make the position of tooltip not overlap to the hovered component.
      val p =
        Point(interactionPane.locationOnScreen).apply {
          translate(swingX + TOOLTIPS_MOUSE_GAP, swingY + TOOLTIPS_MOUSE_GAP)
        }
      popup.show(RelativePoint.fromScreen(p))
    }

    /**
     * Create the tooltips panel for the given [viewInfo]. TODO: Show the scale attribute. TODO:
     * Show the color/theme attribute
     */
    private fun createTooltipPanel(viewInfo: ComposeViewInfo): JPanel {
      val bounds = viewInfo.bounds
      val methodName = viewInfo.sourceLocation.methodName
      val size = "${bounds.width} x ${bounds.height}"
      val location = "${bounds.left}, ${bounds.top}"

      val htmlBuilder = HtmlBuilder()
      if (methodName != "") {
        htmlBuilder.addBold(methodName).addNbsp().coloredText(colorAttributeValue, size)
      } else {
        htmlBuilder
          .coloredText(colorAttributeName, "size")
          .addNbsp()
          .coloredText(colorAttributeValue, size)
      }
      htmlBuilder.newline()
      htmlBuilder
        .coloredText(colorAttributeName, "position")
        .addNbsp()
        .coloredText(colorAttributeValue, location)
      val html = htmlBuilder.html

      return HelpTooltip().setDescription(html).createTipPanel()
    }
  }
}
