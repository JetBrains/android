/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ui.ColorUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.labelFont
import com.intellij.util.ui.StyleSheetUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.net.URI
import java.net.URISyntaxException
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

class HtmlLabel : JEditorPane() {
  init {
    addHyperlinkListener(
      object : HyperlinkAdapter() {
        override fun hyperlinkActivated(event: HyperlinkEvent) {
          if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            val uri = event.description
            try {
              BrowserLauncher.instance.browse(URI(uri))
            } catch (_: URISyntaxException) {}
          }
        }
      }
    )
  }

  override fun getMaximumSize(): Dimension? = preferredSize
  override fun updateUI() {
    super.updateUI()
    val newCss = createCss(font, foreground)
    val kit = HTMLEditorKitBuilder.simple()
    kit.styleSheet = newCss

    val oldText = text
    editorKit = kit
    text = oldText
  }

  companion object {
    @JvmStatic
    fun setUpAsHtmlLabel(editorPane: JEditorPane) {
      setUpAsHtmlLabel(editorPane, labelFont)
    }

    @JvmStatic
    fun setUpAsHtmlLabel(editorPane: JEditorPane, font: Font) {
      setUpAsHtmlLabel(editorPane, font, editorPane.foreground)
    }

    @JvmStatic
    fun setUpAsHtmlLabel(editorPane: JEditorPane, font: Font, foreground: Color) {
      editorPane.isEditable = false
      editorPane.setOpaque(false)
      editorPane.putClientProperty(HONOR_DISPLAY_PROPERTIES, true)

      val kit = HTMLEditorKitBuilder.simple()
      kit.styleSheet = createCss(font, foreground)
      editorPane.setEditorKit(kit)
    }


    private fun createCss(font: Font, foreground: Color) = StyleSheetUtil.getDefaultStyleSheet().apply {
      val linkColor = "#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)
      addRule(
        """
        |body {
        |  font-family: ${font.family};
        |  font-size: ${font.size}pt;
        |  color: ${ColorUtil.toHtmlColor(foreground)};
        |}
        |ol {
        |  padding-left: 0px;
        |  margin-left: 35px;
        |  margin-top: 0px;
        |}
        |ol li {
        |  margin-left: 0px;
        |  padding-left: 0px;
        |  list-style-type: decimal;
        |}
        |a {
        |  color: $linkColor;
        |  text-decoration: none;
        |}
        """.trimMargin()
      )
    }
  }
}
