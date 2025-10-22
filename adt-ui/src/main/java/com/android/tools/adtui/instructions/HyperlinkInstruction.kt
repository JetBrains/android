/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.instructions

import com.intellij.ui.HyperlinkLabel
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent
import org.jetbrains.annotations.TestOnly

/**
 * An instruction for rendering an URL. It wraps a [HyperlinkLabel] which supports all the proper
 * formatting and interactions users would perform on a typical URL. By default, it will handle
 * mouse clicks by browsing to the specified url, unless action is specified, in which case the
 * action will be run when the link is clicked. When specifying an action, a suffix [Icon] can be
 * specified. It will be displayed after the text. It's not possible to specify a suffix icon when a
 * url is specified.
 */
class HyperlinkInstruction
private constructor(
  font: Font,
  text: String,
  url: String? = null,
  suffixIcon: Icon? = null,
  action: ((InputEvent) -> Unit)? = null,
) : RenderInstruction() {

  constructor(
    font: Font,
    text: String,
    url: String,
  ) : this(font = font, text = text, url = url, suffixIcon = null, action = null)

  constructor(
    font: Font,
    text: String,
    action: ((InputEvent) -> Unit)? = null,
    suffixIcon: Icon? = null,
  ) : this(font = font, text = text, url = null, suffixIcon = suffixIcon, action = action)

  private val hyperlinkLabel = HyperlinkLabel(text)
  private val size: Dimension

  init {
    check(url == null || suffixIcon == null) { "Cannot specify both a url and a suffix icon" }
    hyperlinkLabel.setFont(font)
    if (suffixIcon != null) {
      hyperlinkLabel.setIcon(suffixIcon)
      hyperlinkLabel.setIconAtRight(true)
    }

    if (url != null) {
      hyperlinkLabel.setHyperlinkTarget(url)
    }

    if (action != null) {
      hyperlinkLabel.addHyperlinkListener { e -> action(e.inputEvent) }
    }

    setMouseHandler { hyperlinkLabel.dispatchEvent(it) }

    // save size after setting text and icon in HyperlinkLabel, to get the real size. Otherwise text
    // and icon might overlap.
    size = hyperlinkLabel.getPreferredSize()
  }

  override fun getSize() = size

  override fun getCursorIcon(): Cursor? {
    return if (hyperlinkLabel.isCursorSet) hyperlinkLabel.getCursor() else null
  }

  override fun render(c: JComponent, g2d: Graphics2D, bounds: Rectangle) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
    )
    g2d.translate(bounds.x, bounds.y)
    hyperlinkLabel.bounds = bounds
    hyperlinkLabel.paint(g2d)
    g2d.translate(-bounds.x, -bounds.y)
  }

  @get:TestOnly val displayTextForTests = hyperlinkLabel.text
}
