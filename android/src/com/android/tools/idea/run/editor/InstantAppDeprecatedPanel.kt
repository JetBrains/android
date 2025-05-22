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
package com.android.tools.idea.run.editor

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * A banner to show Instant App support is about to go away in the next release.
 */
class InstantAppDeprecatedPanel : JPanel(GridBagLayout()) {
  init {
    isOpaque = false
    val gbc =
      GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        gridx = 1
        anchor = GridBagConstraints.WEST
      }

    add(createContentPanel(), gbc)

    gbc.apply { gridx = 0 }
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
              JBLabel(AllIcons.General.Warning)
                .apply { isOpaque = false }
            add(iconLabel, BorderLayout.NORTH)
            isOpaque = false
          }
        add(iconPanel, BorderLayout.WEST)

        val contentPanel = JPanel(BorderLayout(0, JBUI.scale(8))).apply { isOpaque = false }
        val descriptionTextPane =
          JTextArea().apply {
            text = "Instant Apps support will be removed by Google Play in December 2025. " +
                   "Publishing and all Google Play Instant APIs will no longer work. " +
                   "Tooling support will be removed in Android Studio Otter Feature Drop."
            isEditable = false
            isFocusable = false
            wrapStyleWord = true
            lineWrap = true
            columns = 50
            font = JBFont.label()
            isOpaque = false
          }
        contentPanel.add(descriptionTextPane)
        add(contentPanel)
      }

      override fun paintBorder(g: Graphics) {
        super.paintComponent(g)
        with(g as Graphics2D) {
          val color = Banner.WARNING_BACKGROUND
          setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
          g.color = color
          g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)
          g.color = Banner.WARNING_BORDER_COLOR
          g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
        }
      }
    }
}