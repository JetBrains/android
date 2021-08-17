/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute

/**
 * A label for displaying an actionable link.
 *
 * The link is displayed in a small font with an underline with the following options:
 * - [showAsLink] if false, show as normal text (in case the link could not be resolved)
 * - [strikeout] if true, the label is shown with strikeout (typically used for overridden values)
 * - [hyperLinkListeners] add a listener to be notified when the link is activated through mouse or keyboard
 */
class CommonHyperLinkLabel(
  private val showAsLink: Boolean = true,
  private val strikeout: Boolean = false
) : JBLabel() {
  val hyperLinkListeners = mutableListOf<() -> Unit>()
  var normalForegroundColor: Color = JBColor.BLACK
    private set
  private var initialized = true

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    isFocusable = showAsLink
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    updateUI()
    registerActionKey({ fireHyperLinkActivated() }, KeyStrokes.ENTER, "enter")
    registerActionKey({ fireHyperLinkActivated() }, KeyStrokes.SPACE, "space")
    addMouseListener(object : MouseAdapter() {
      // Use mousePressed instead of mouseClicked, for table cell renderer support.
      // (the click event may not happen if the mouse pressed is also causing a cell editor to be created).
      override fun mousePressed(event: MouseEvent) {
        fireHyperLinkActivated()
        event.consume()
      }
    })
  }

  override fun updateUI() {
    super.updateUI()
    if (initialized) {
      font = getSmallFont(showAsLink, strikeout)
      normalForegroundColor = if (showAsLink) JBUI.CurrentTheme.Link.linkColor() else UIUtil.getLabelForeground()
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (hasFocus() && g is Graphics2D) {
      val insets = this.insets
      val textWidth = (getFontMetrics(font).stringWidth(text) + (insets.left + insets.right)).coerceAtMost(width)
      DarculaUIUtil.paintFocusBorder(g, textWidth, height, 0f, true)
    }
  }

  private fun fireHyperLinkActivated() {
    if (showAsLink) {
      hyperLinkListeners.forEach { it() }
    }
  }

  private fun getSmallFont(showAsLink: Boolean, strikethrough: Boolean): Font {
    val font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    @Suppress("UNCHECKED_CAST")
    val attributes = font.attributes as MutableMap<TextAttribute, Any?>
    if (showAsLink) {
      attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
    }
    if (strikethrough) {
      attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
    }
    return Font(attributes)
  }
}
