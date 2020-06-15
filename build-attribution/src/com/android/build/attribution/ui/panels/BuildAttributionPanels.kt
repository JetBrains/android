/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issueIcon
import com.android.build.attribution.ui.percentageString
import com.android.utils.HtmlBuilder
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

const val CRITICAL_PATH_LINK = "https://developer.android.com/r/tools/build-attribution/critical-path"

interface TreeLinkListener<T> {
  fun clickedOn(target: T)
}

fun pluginInfoPanel(
  pluginUiData: CriticalPathPluginUiData,
  listener: TreeLinkListener<TaskIssueType>,
  analytics: BuildAttributionUiAnalytics
): JComponent = JBPanel<JBPanel<*>>(VerticalLayout(0)).apply {
  val pluginText = HtmlBuilder()
    .openHtmlBody()
    .add(
      "This plugin has ${pluginUiData.criticalPathTasks.size} ${pluralize("task", pluginUiData.criticalPathTasks.size)} " +
      "of total duration ${pluginUiData.criticalPathDuration.durationString()} (${pluginUiData.criticalPathDuration.percentageString()})"
    )
    .newline()
    .add("determining this build's duration.")
    .closeHtmlBody()
  add(DescriptionWithHelpLinkLabel(pluginText.html, CRITICAL_PATH_LINK, analytics))
  add(JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
    border = JBUI.Borders.emptyTop(15)
    add(JBLabel("Warnings detected").withFont(JBUI.Fonts.label().asBold()))
    for (issueGroup in pluginUiData.issues) {
      add(HyperlinkLabel("${issueGroup.type.uiName} (${issueGroup.size})").apply {
        addHyperlinkListener { listener.clickedOn(issueGroup.type) }
        border = JBUI.Borders.emptyLeft(15)
        setIcon(issueIcon(issueGroup.type))
      })
    }
    if (pluginUiData.issues.isEmpty()) {
      add(JLabel("No warnings detected"))
    }
  })
  withPreferredWidth(400)
}

fun taskInfoPanel(taskData: TaskUiData, listener: TreeLinkListener<TaskIssueUiData>): JPanel {
  val infoPanel = JBPanel<JBPanel<*>>(GridBagLayout())
  val taskDescription = htmlTextLabel(
    HtmlBuilder()
      .openHtmlBody()
      .add(
        if (taskData.onLogicalCriticalPath)
          "This task frequently determines build duration because of dependencies between its inputs/outputs and other tasks."
        else
          "This task occasionally determines build duration because of parallelism constraints introduced by number of cores or other tasks in the same module."
      )
      .closeHtmlBody()
      .html
  )
  val taskInfo = htmlTextLabel(
    HtmlBuilder()
      .openHtmlBody()
      .add("Module: ${taskData.module}")
      .newline()
      .add("Plugin: ${taskData.pluginName}")
      .newline()
      .add("Type: ${taskData.taskType}")
      .newline()
      .add("Duration: ${taskData.executionTime.durationString()} / ${taskData.executionTime.percentageString()}")
      .newline()
      .add("Executed incrementally: ${if (taskData.executedIncrementally) "Yes" else "No"}")
      .closeHtmlBody()
      .html
  )
  val issuesList = JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
    add(JBLabel("Issues with this task").withFont(JBUI.Fonts.label().asBold()))
    for (issue in taskData.issues) {
      val label = HyperlinkLabel(issue.type.uiName)
      label.addHyperlinkListener { listener.clickedOn(issue) }
      label.border = JBUI.Borders.emptyLeft(15)
      label.setIcon(issueIcon(issue.type))
      add(label)
    }
    if (taskData.issues.isEmpty()) {
      add(JLabel("No issues found"))
    }
  }
  val reasonsToRunHeader = JBLabel("Reason task ran").withFont(JBUI.Fonts.label().asBold())
  val reasonsList = reasonsToRunList(taskData)

  val c = GridBagConstraints()
  c.weightx = 1.0
  c.gridx = 0
  c.gridy = 0
  c.anchor = GridBagConstraints.FIRST_LINE_START
  c.fill = GridBagConstraints.HORIZONTAL
  c.insets = JBUI.insetsBottom(8)
  infoPanel.add(taskDescription, c)

  c.gridy = 1
  infoPanel.add(taskInfo, c)

  c.gridy = 2
  infoPanel.add(issuesList, c)

  c.gridy = 3
  c.insets = JBUI.insetsTop(8)
  infoPanel.add(reasonsToRunHeader, c)

  c.gridy = 4
  c.insets = JBUI.insetsTop(8)
  c.fill = GridBagConstraints.BOTH
  c.weighty = 1.0
  infoPanel.add(reasonsList, c)

  infoPanel.withPreferredWidth(sequenceOf<JComponent>(taskInfo, issuesList).map { it.preferredSize.width }.max()!!)
  return infoPanel
}

private fun reasonsToRunList(taskData: TaskUiData) = htmlTextLabel(createReasonsText(taskData.reasonsToRun))

private fun createReasonsText(reasons: List<String>): String = if (reasons.isEmpty()) {
  "No info"
}
else {
  reasons.joinToString(separator = "<br/>") { wrapPathToSpans(it).replace("\n", "<br>") }
}

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
private fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"

fun verticalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredWidth(1)
  .withMaximumWidth(1)
  .withMinimumWidth(1)

fun createIssueTypeListPanel(issuesGroup: TaskIssuesGroup, listener: TreeLinkListener<TaskIssueUiData>): JComponent =
  JBPanel<JBPanel<*>>().apply {
    layout = VerticalLayout(6)
    issuesGroup.issues.forEach {
      add(HyperlinkLabel(it.task.taskPath).apply { addHyperlinkListener { _ -> listener.clickedOn(it) } })
    }
  }

fun criticalPathHeader(prefix: String, duration: String): JComponent =
  headerLabel("${prefix} determining this build's duration (${duration})")

fun headerLabel(text: String): JLabel = JBLabel(text).withFont(JBUI.Fonts.label(13f).asBold()).apply {
  name = "pageHeader"
}

/**
 * Label with auto-wrapping turned on that accepts html text.
 * Used in Build Analyzer to render long multi-line text.
 */
fun htmlTextLabel(html: String): JBLabel = JBLabel(html).apply {
  setAllowAutoWrapping(true)
  setCopyable(true)
  isFocusable = false
  verticalTextPosition = SwingConstants.TOP
}
