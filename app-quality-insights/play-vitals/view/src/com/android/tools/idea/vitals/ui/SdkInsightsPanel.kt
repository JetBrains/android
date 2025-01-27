/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.insights.ui.transparentPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.util.addComponentListener
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.text.html.HTMLDocument

private const val SEE_MORE = "more"
private const val EXPANDED = "expanded"

class SdkInsightsPanel(category: String, title: String, private val body: String) :
  JPanel(BorderLayout()) {
  private val iconLabel = JBLabel(AllIcons.Actions.IntentionBulb)
  private val categoryLabel = JBLabel(category).withFont(JBFont.label().asBold())
  private val titleLabel = JBLabel(title).withFont(JBFont.label().asItalic().lessOn(2f))

  private val truncatedLabel = JBLabel(body)
  private val seeMoreLinkLabel: HyperlinkLabel =
    HyperlinkLabel("Show more").apply {
      isFocusable = true
      // Restricting the max size to preferred size is needed to keep the external link icon
      // adjacent to the link.
      maximumSize = preferredSize
      addHyperlinkListener { expand() }
    }

  private val topPanel = transparentPanel(FlowLayout(FlowLayout.LEFT))
  private val seeMorePanel = JPanel(TabularLayout("*,Fit"))

  // The url wrapped with () parentheses and is located at the end of the body.
  private val urlRegex = Regex("\\(([^)]+)\\)\\s*$", RegexOption.MULTILINE)

  private val expandedLabel = HtmlLabel().apply { text = replaceUrlsWithHtmlLinks(body) }

  private val scrollPane =
    JBScrollPane(expandedLabel).apply {
      verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
      horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
    }

  private val expandPanel =
    transparentPanel(BorderLayout()).apply { add(scrollPane, BorderLayout.CENTER) }

  private val cardPanel =
    transparentPanel(CardLayout()).apply {
      add(seeMorePanel, SEE_MORE)
      add(expandPanel, EXPANDED)
    }

  init {
    border = JBUI.Borders.empty(7)

    topPanel.add(iconLabel)
    topPanel.add(categoryLabel)
    topPanel.add(titleLabel)

    truncatedLabel.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          seeMoreLinkLabel.isVisible =
            if (truncatedLabel.preferredSize.width <= truncatedLabel.width) {
              false
            } else {
              true
            }
        }
      }
    )

    seeMorePanel.add(truncatedLabel, TabularLayout.Constraint(0, 0))
    seeMorePanel.add(seeMoreLinkLabel, TabularLayout.Constraint(0, 1))

    add(topPanel, BorderLayout.NORTH)
    add(cardPanel, BorderLayout.CENTER)
    cardPanel.preferredSize = seeMorePanel.preferredSize
  }

  private fun expand() {
    (cardPanel.layout as CardLayout).show(cardPanel, EXPANDED)
    cardPanel.preferredSize = expandPanel.preferredSize
    scrollPane.horizontalScrollBar.value = 0
    // Resizing studio after the expanded label is visible will leave a blank space
    // between this panel and the tabbed pane.
    // Resize cardPanel to expandPanel's preferred size to leave no gap in between.
    expandPanel.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          cardPanel.preferredSize = expandPanel.preferredSize
        }
      }
    )
  }

  private fun replaceUrlsWithHtmlLinks(body: String) =
    body.replace(urlRegex) { matchResult ->
      "(<a href=\"${matchResult.groupValues[1]}\">${matchResult.groupValues[1]}</a>)"
    }

  override fun updateUI() {
    super.updateUI()
    categoryLabel?.withFont(JBFont.label().asBold())
    titleLabel?.withFont(JBFont.label().asItalic().lessOn(2f))
    val bodyRule =
      "body { font-family: ${JBFont.label().family}; font-size: ${JBFont.label().size}pt; }"
    (expandedLabel?.document as? HTMLDocument)?.styleSheet?.addRule(bodyRule)
  }

  override fun paintComponent(g: Graphics) {
    g.color = background
    g.fillRoundRect(0, 0, width, height, 16, 16)
  }
}
