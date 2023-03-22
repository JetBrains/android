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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.AdtUiUtils.ShrinkDirection.TRUNCATE_START
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.IssueState
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.JTable

object AppInsightsIssuesTableCellRenderer : AppInsightsTableCellRenderer {

  private val pendingRequestIcon =
    SimpleColoredComponent().apply {
      icon = offlineModeIcon
      iconTextGap = 0
      border = JBUI.Borders.empty()
      isOpaque = false
      isVisible = false
    }
  private val renderer = SimpleColoredComponent().apply { isOpaque = false }
  private val leftPanel =
    JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
      add(pendingRequestIcon)
      add(renderer)
    }
  private val signalPanel =
    JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
  private val rendererPanel =
    JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty()
      add(leftPanel, BorderLayout.WEST)
      add(signalPanel, BorderLayout.EAST)
    }

  override fun updateRenderer() {
    renderer.font = StartupUiUtil.getLabelFont()
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any,
    selected: Boolean,
    focused: Boolean,
    viewRowIndex: Int,
    viewColumnIndex: Int
  ): Component {
    val issue = value as AppInsightsIssue
    renderer.removeAll()
    renderer.clear()
    val foreground = if (selected) table.selectionForeground else table.foreground
    renderer.icon =
      getFatalityIcon(
        issue.issueDetails.fatality,
        selected,
        foreground,
        issue.issueDetails.notesCount > 0
      )
    var availableWidth = table.columnModel.getColumn(0).width.toFloat() - JBUI.scale(24)

    signalPanel.removeAll()
    issue.issueDetails.signals.forEach {
      val iconLabel =
        SimpleColoredComponent().apply {
          icon = it.icon
          iconTextGap = 0
          border = JBUI.Borders.empty()
          isOpaque = false
        }
      signalPanel.add(iconLabel)
      availableWidth -= JBUIScale.scale(20)
    }

    pendingRequestIcon.isVisible = issue.pendingRequests > 0

    val (className, methodName) = issue.issueDetails.getDisplayTitle()
    renderer.foreground = foreground
    val style =
      when (issue.state) {
        IssueState.OPEN,
        IssueState.CLOSING -> SimpleTextAttributes.STYLE_PLAIN
        IssueState.CLOSED,
        IssueState.OPENING -> SimpleTextAttributes.STYLE_STRIKEOUT
      }
    if (methodName.isEmpty()) {
      renderClassText(className, style, availableWidth)
    } else {
      val methodAttrs = SimpleTextAttributes(style or SimpleTextAttributes.STYLE_BOLD, null)
      val methodFont = renderer.font.deriveFont(methodAttrs.style)
      val methodFontMetrics = rendererPanel.getFontMetrics(methodFont)
      val methodText =
        AdtUiUtils.shrinkToFit(methodName, methodFontMetrics, availableWidth, TRUNCATE_START)
      availableWidth -= methodFontMetrics.stringWidth(methodText)

      if (methodText == methodName) {
        renderClassText(className, style, availableWidth)
      }
      renderer.append(methodText, methodAttrs)
    }
    rendererPanel.toolTipText =
      "<html>${issue.issueDetails.title}<br>${issue.issueDetails.subtitle}</html>"
    rendererPanel.background = if (selected) table.selectionBackground else table.background
    renderer.border = JBUI.Borders.empty()
    return rendererPanel
  }

  private fun renderClassText(className: String, style: Int, availableWidth: Float) {
    val classAttrs = SimpleTextAttributes(style, null)
    val classFont = renderer.font.deriveFont(classAttrs.style)
    val classText = "$className."
    renderer.append(
      AdtUiUtils.shrinkToFit(
        classText,
        rendererPanel.getFontMetrics(classFont),
        availableWidth,
        TRUNCATE_START
      ),
      classAttrs
    )
  }
}
