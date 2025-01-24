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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.JTextArea

class ServiceDeprecatedPanel(
  private val deprecationData: DevServicesDeprecationData,
  private val updateCallback: () -> Unit = {},
) : JPanel(GridBagLayout()) {
  init {
    isOpaque = false
    val gbc =
      GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        gridx = 1
        anchor = GridBagConstraints.CENTER
      }

    add(createContentPanel(), gbc)

    gbc.apply { gridx = 0 }
    add(createSpacer(), gbc)
    add(createSpacer(), gbc.apply { gridx = 2 })
  }

  private fun createSpacer() =
    JPanel().apply {
      border = JBUI.Borders.empty()
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(JBUI.scale(20), 0)
    }

  private fun createContentPanel() =
    object : JPanel(BorderLayout(JBUI.scale(12), 0)) {

      init {
        isOpaque = false
        border = JBUI.Borders.empty(16)
        val iconPanel =
          JPanel(BorderLayout()).apply {
            val iconLabel =
              JBLabel(
                  if (deprecationData.showUpdateAction) {
                    AllIcons.General.Warning
                  } else {
                    AllIcons.General.Error
                  }
                )
                .apply { isOpaque = false }
            add(iconLabel, BorderLayout.NORTH)
            isOpaque = false
          }
        add(iconPanel, BorderLayout.WEST)

        val contentPanel = JPanel(BorderLayout(0, JBUI.scale(8))).apply { isOpaque = false }

        val headerLabel =
          JBLabel(deprecationData.header).apply {
            font = JBFont.label().asBold()
            isOpaque = false
          }
        contentPanel.add(headerLabel, BorderLayout.NORTH)

        val descriptionTextPane =
          JTextArea().apply {
            text = deprecationData.description
            isEditable = false
            isFocusable = false
            wrapStyleWord = true
            lineWrap = true
            columns = 50
            font = JBFont.label()
            isOpaque = false
          }
        contentPanel.add(descriptionTextPane)

        val bottomPanel =
          JPanel(HorizontalLayout(8)).apply {
            isOpaque = false
            if (deprecationData.showUpdateAction) {
              val updateLabel = createHyperlinkLabel("Update Android Studio") { updateCallback() }
              add(updateLabel)
            }
            if (deprecationData.moreInfoUrl.isNotEmpty()) {
              val moreInfoLabel =
                createHyperlinkLabel("More info") {
                  BrowserUtil.browse(deprecationData.moreInfoUrl)
                }
              add(moreInfoLabel)
            }
          }
        contentPanel.add(bottomPanel, BorderLayout.SOUTH)
        add(contentPanel)
      }

      override fun paintBorder(g: Graphics) {
        super.paintComponent(g)
        with(g as Graphics2D) {
          val color =
            if (deprecationData.showUpdateAction) {
              Banner.WARNING_BACKGROUND
            } else {
              Banner.ERROR_BACKGROUND
            }
          setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
          g.color = color
          g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)
          g.color = color.brighter()
          g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
        }
      }
    }

  private fun createHyperlinkLabel(text: String, action: () -> Unit) =
    HyperlinkLabel().apply {
      setHyperlinkText(text)
      addHyperlinkListener { action() }
      isOpaque = false
    }
}
