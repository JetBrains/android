/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.property.panel.impl.ui

import com.google.common.html.HtmlEscapers
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel

private const val RIGHT_OVERLAY_MARGIN = 6

/**
 * A label that handles text that is too wide for the available space.
 *
 * Use the actualText property instead of the text property to specify the text in this component.
 *
 * If the text is too wide ellipses will be shown at the end of the text (standard JLabel behaviour).
 * When the mouse is hovering over the text a popup will complete the hidden text.
 */
class ExpandableLabel : JLabel() {
  private val expandableLabelHandler = ExpandableLabelHandler(this)
  private var htmlText: String = ""
  private var showEllipsis = true
    set(value) {
      field = value
      updateText()
    }

  var actualText: String = ""
    set(value) {
      field = value
      htmlText = if (value.isEmpty()) "" else toHtml(value)
      updateText()
    }

  private fun updateText() {
    super.setText(if (showEllipsis) actualText else htmlText)
  }

  /**
   * Hack: Determine when the popup was closed, and start showing the ellipsis again.
   */
  override fun repaint(r: Rectangle) {
    super.repaint(r)
    showEllipsis = !expandableLabelHandler.isShowing
  }

  private fun toHtml(value: String): String = "<html><nobr>${HtmlEscapers.htmlEscaper().escape(value)}</nobr></html>"

  /**
   * Handles expansion of property labels.
   *
   * The tricky part of this code is to be able to show ellipsis in labels that are too wide,
   * but remove the ellipsis when the text is expanded.
   */
  private class ExpandableLabelHandler(component: ExpandableLabel) :
    AbstractExpandableItemsHandler<ExpandableLabel, ExpandableLabel>(component) {

    private val renderer: JLabel = JLabel()

    override fun getCellKeyForPoint(point: Point): ExpandableLabel = myComponent

    override fun getCellRendererAndBounds(key: ExpandableLabel): ComponentBounds? {
      if (key.preferredSize.width <= key.width) {
        // If the text fits the current size there is nothing to expand
        return null
      }
      if (!ApplicationManager.getApplication().isActive) {
        // Only expand if this is the active application
        return null
      }

      // This renderer is used to display the text that expands to the right of the original label component.
      // This is done in a popup in [AbstractExpandableItemsHandler].
      renderer.text = key.text
      renderer.icon = key.icon
      renderer.font = key.font
      renderer.foreground = key.foreground

      // Record the label being expanded and hide the ellipsis at the end of the text.
      key.showEllipsis = false
      return ComponentBounds.create(renderer, computeOverlayBounds(key))
    }

    override fun isPaintBorder() = false

    override fun doFillBackground(height: Int, width: Int, g: Graphics2D) {
      g.color = computeBackgroundColor() ?: return
      g.fillRect(0, 0, width, height)
    }

    private fun computeBackgroundColor(): Color? {
      var component: Component = myComponent
      while (!component.isOpaque) {
        component = component.parent ?: return null
      }
      return component.background
    }

    private fun computeOverlayBounds(key: ExpandableLabel): Rectangle {
      val bounds = Rectangle(0, 0, key.preferredSize.width, key.height)

      // Give a little extra room in the overlay
      //noinspection JbUiStored
      bounds.width += JBUI.scale(RIGHT_OVERLAY_MARGIN)

      return bounds
    }
  }
}