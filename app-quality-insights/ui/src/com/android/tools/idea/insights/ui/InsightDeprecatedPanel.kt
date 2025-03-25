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
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SwingConstants

class InsightDeprecatedPanel(project: Project, data: DevServicesDeprecationData) :
  JPanel(GridBagLayout()) {
  init {
    val gbc =
      GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        gridx = 1
        anchor = GridBagConstraints.CENTER
        weightx = 0.1
      }
    add(createContentPanel(project, data), gbc)

    gbc.apply {
      gridx = 0
      weightx = 0.25
    }
    add(createSpacer(), gbc)
    add(createSpacer(), gbc.apply { gridx = 3 })
  }

  private fun createSpacer() =
    JPanel().apply {
      border = JBUI.Borders.empty()
      minimumSize = JBDimension(16, 0)
      preferredSize = JBDimension(2000, 0)
    }

  private fun createContentPanel(project: Project, data: DevServicesDeprecationData) =
    JPanel(BorderLayout(0, JBUI.scale(12))).apply {
      val header = createHeader(data.header)
      add(header, BorderLayout.NORTH)
      val description = createDescription(data.description)
      add(description)

      val bottomPanel =
        JPanel(VerticalLayout(JBUI.scale(12))).apply {
          isOpaque = false
          if (data.showUpdateAction) {
            add(createUpdateButton(project))
          }
          if (data.moreInfoUrl.isNotEmpty()) {
            add(createMoreInfoLabel(data.moreInfoUrl))
          }
        }
      add(bottomPanel, BorderLayout.SOUTH)

      description.maximumSize = JBDimension(2 * header.preferredWidth, Int.MAX_VALUE)
    }

  private fun createHeader(text: String) =
    JBLabel(text).apply {
      font = JBFont.label().biggerOn(2f).asBold()
      border = JBUI.Borders.empty(0, 24)
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
    }

  private fun createDescription(t: String) =
    object : JTextPane() {
      init {
        isOpaque = false
        editorKit = HTMLEditorKitBuilder.simple()
        contentType = "text/html"
        text = "<p align=\"center\">$t</p>"
        isEditable = false
        isFocusable = false
        border = JBUI.Borders.empty()
      }

      override fun updateUI() {
        super.updateUI()
        foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
      }
    }

  private fun createUpdateButton(project: Project) =
    JPanel().apply {
      val button =
        JButton("Update Android Studio").apply {
          addActionListener { UpdateChecker.updateAndShowResult(project) }
        }
      add(button)
    }

  private fun createMoreInfoLabel(url: String) =
    JPanel().apply {
      val label =
        HyperlinkLabel().apply {
          setHyperlinkText("More info")
          addHyperlinkListener { BrowserUtil.browse(url) }
          icon = AllIcons.General.ContextHelp
        }
      add(label)
    }
}
