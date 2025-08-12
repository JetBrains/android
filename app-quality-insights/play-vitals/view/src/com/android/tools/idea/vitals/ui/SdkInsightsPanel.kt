/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.ui.components.JBLabel
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Cursor
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

class SdkInsightsPanel(private val category: String, private val title: String, body: String) :
  BorderLayoutPanel() {
  private val iconLabel =
    JBLabel(createTitleText(category, title), AllIcons.Actions.IntentionBulb, SwingConstants.LEFT)
      .apply { verticalAlignment = SwingConstants.BOTTOM }

  init {
    addToTop(iconLabel)
    val textPane = TextPaneWithShowMore(body)

    addToCenter(textPane)
  }

  private fun createTitleText(category: String, title: String) =
    "<html>${createHtmlText("b", category, 10)} ${createHtmlText("i", title, 9)}</html>"

  private fun createHtmlText(style: String, text: String, fontSize: Int) =
    "<$style style='font-size:${JBUI.scale(fontSize)}px'>$text</$style>"

  override fun updateUI() {
    super.updateUI()
    // This helps resize the text on zoom in/out
    @Suppress("UNNECESSARY_SAFE_CALL")
    iconLabel?.text = createTitleText(category, title)
  }

  private class TextPaneWithShowMore(private val body: String) : JTextPane() {
    private var isExpanded = false
      set(value) {
        field = value
        handleState()
      }

    private val showLabel =
      JBLabel("Show more").apply {
        alignmentY = 0.8f
        foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        border = JBUI.Borders.emptyLeft(2)

        addMouseListener(
          object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
              cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
              font =
                JBFont.label()
                  .deriveFont(
                    font.attributes.plus(Pair(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON))
                  )
            }

            override fun mouseExited(e: MouseEvent?) {
              cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
              font = JBFont.label()
            }

            override fun mouseClicked(e: MouseEvent?) {
              isExpanded = !isExpanded
              cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
            }
          }
        )
      }
    // The url wrapped with () parentheses and is located at the end of the body.
    private val urlRegex = Regex("\\(([^)]+)\\)\\s*$", RegexOption.MULTILINE)

    private val htmlBody = body.addWordBreaks().replaceUrl()

    init {
      editorKit = HTMLEditorKitBuilder.simple()
      isEditable = false
      isFocusable = false

      handleState()

      addComponentListener(
        object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent) {
            handleState()
          }
        }
      )

      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          BrowserUtil.browse(it.url.toString())
        }
      }
    }

    private fun handleState() {
      if (isExpanded) {
        text = htmlBody
        showLabel.text = "Show less"
        insertComponent(showLabel)
      } else {
        val truncatedText = truncateText(this, body, showLabel)
        text = truncatedText
        showLabel.text = "Show more"
        if (truncatedText != body) {
          insertComponent(showLabel)
        }
      }
      invokeLater {
        revalidate()
        parent?.revalidate()
      }
    }

    private fun String.replaceUrl() =
      replace(urlRegex) { matchResult ->
        "(<a href=\"${matchResult.groupValues[1]}\">${matchResult.groupValues[1]}</a>)"
      }

    private fun String.addWordBreaks() = replace(" ", " <wbr>")
  }
}

fun truncateText(textPane: JTextPane, text: String, showLabel: JBLabel): String {
  val metrics = textPane.getFontMetrics(textPane.font)
  val availableWidth = textPane.width - showLabel.preferredWidth - 15
  return if (metrics.stringWidth(text) > availableWidth) {
    var truncatedStringWidth = 0
    var idx = 0
    val ellipsisWidth = metrics.stringWidth("...")
    buildString {
      while (idx < text.length) {
        val char = text[idx]
        truncatedStringWidth += metrics.stringWidth(char.toString())
        if (truncatedStringWidth + ellipsisWidth >= availableWidth) {
          break
        }
        append(char)
        idx += 1
      }
      append("...")
    }
  } else {
    text
  }
}
