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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issueIcon
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.android.utils.HtmlBuilder
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

interface TreeLinkListener<T> {
  fun clickedOn(target: T)
}

@Deprecated("Used only in old navigation model, to be removed with cleanup.")
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
  add(DescriptionWithHelpLinkLabel(pluginText.html, BuildAnalyzerBrowserLinks.CRITICAL_PATH, analytics::helpLinkClicked))
  add(JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
    border = JBUI.Borders.emptyTop(15)
    add(JBLabel("Warnings detected").apply { font = font.deriveFont(Font.BOLD) })
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

@Deprecated("Used only in old navigation model, to be removed with cleanup.")
fun taskInfoPanel(
  taskData: TaskUiData,
  analytics: BuildAttributionUiAnalytics,
  issueReporter: TaskIssueReporter
): JPanel = taskDetailsPanel(
  taskData,
  helpLinkListener = analytics::helpLinkClicked,
  generateReportClickedListener = {
    analytics.bugReportLinkClicked()
    issueReporter.reportIssue(taskData)
  }
)

fun taskDetailsPanel(
  taskData: TaskUiData,
  helpLinkListener: (BuildAnalyzerBrowserLinks) -> Unit,
  generateReportClickedListener: (TaskUiData) -> Unit
): JPanel {
  val determinesBuildDurationLine = if (taskData.onLogicalCriticalPath)
    "This task frequently determines build duration because of dependencies between its inputs/outputs and other tasks."
  else
    "This task occasionally determines build duration because of parallelism constraints introduced by number of cores or other tasks in the same module."
  val taskInfo = htmlTextLabelWithLinesWrap("""
      ${determinesBuildDurationLine}<br/>
      <br/>
      <b>Duration:</b>  ${taskData.executionTime.durationString()} / ${taskData.executionTime.percentageString()}<br/>
      Sub-project: ${taskData.module}<br/>
      Plugin: ${taskData.pluginName}<br/>
      Type: ${taskData.taskType}<br/>
      <br/>
      <b>Warnings</b><br/>
    """.trimIndent())

  val reasonsList = htmlTextLabelWithLinesWrap("""
    <b>Reason task ran</b><br/>
    ${createReasonsText(taskData.reasonsToRun)}
  """.trimIndent())

  val infoPanel = JBPanel<JBPanel<*>>(TabularLayout("*"))
  var row = 0
  infoPanel.add(taskInfo, TabularLayout.Constraint(row++, 0))
  if (taskData.issues.isEmpty()) {
    infoPanel.add(JLabel("No warnings found"), TabularLayout.Constraint(row++, 0))
  }
  else {
    if (taskData.sourceType != PluginSourceType.BUILD_SRC) {
      infoPanel.add(generateReportLinkLabel(taskData, generateReportClickedListener), TabularLayout.Constraint(row++, 0))
    }
    for ((index, issue) in taskData.issues.withIndex()) {
      infoPanel.add(
        taskWarningDescriptionPanel(issue, helpLinkListener, index > 0),
        TabularLayout.Constraint(row++, 0)
      )
    }
  }
  reasonsList.border = JBUI.Borders.emptyTop(22)
  infoPanel.add(reasonsList, TabularLayout.Constraint(row, 0))

  return infoPanel
}

private fun taskWarningDescriptionPanel(
  issue: TaskIssueUiData,
  helpLinkClickCallback: (BuildAnalyzerBrowserLinks) -> Unit,
  needSeparatorInFront: Boolean
): JComponent = JPanel().apply {
  name = "warning-${issue.type.name}"
  border = JBUI.Borders.emptyTop(8)
  layout = TabularLayout("Fit,*").setVGap(8)
  if (needSeparatorInFront) {
    add(horizontalRuler(), TabularLayout.Constraint(0, 1))
  }
  add(JBLabel(warningIcon()).withBorder(JBUI.Borders.emptyRight(5)), TabularLayout.Constraint(1, 0))
  add(htmlTextLabelWithFixedLines("<b>${issue.type.uiName}</b>"), TabularLayout.Constraint(1, 1))
  add(DescriptionWithHelpLinkLabel(issue.explanation, issue.helpLink, helpLinkClickCallback), TabularLayout.Constraint(2, 1))
  add(htmlTextLabelWithLinesWrap("<b>Recommendation:</b> ${issue.buildSrcRecommendation}"), TabularLayout.Constraint(3, 1))
}

@Deprecated("Left to support previous version.")
fun generateReportLinkLabel(
  analytics: BuildAttributionUiAnalytics,
  issueReporter: TaskIssueReporter,
  taskData: TaskUiData
): JComponent = generateReportLinkLabel(taskData) {
  analytics.bugReportLinkClicked()
  issueReporter.reportIssue(taskData)
}

fun generateReportLinkLabel(
  taskData: TaskUiData,
  generateReportClicked: (TaskUiData) -> Unit
): JComponent = JPanel().apply {
  layout = FlowLayout(FlowLayout.LEFT, 0, 0)
  add(JLabel("Consider filing a bug to report this issue to the plugin developer. "))
  val link = HyperlinkLabel("Generate report")
  link.addHyperlinkListener { generateReportClicked(taskData) }
  add(link)
}

fun reasonsToRunList(taskData: TaskUiData) = htmlTextLabelWithLinesWrap(createReasonsText(taskData.reasonsToRun))

private fun createReasonsText(reasons: List<String>): String = if (reasons.isEmpty()) {
  "No info"
}
else {
  reasons.joinToString(separator = "<br/>") { wrapPathToSpans(it).replace("\n", "<br>") }
}

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"

fun verticalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredWidth(1)
  .withMaximumWidth(1)
  .withMinimumWidth(1)

fun horizontalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredHeight(1)
  .withMaximumHeight(1)
  .withMinimumHeight(1)

fun createIssueTypeListPanel(issuesGroup: TaskIssuesGroup, listener: TreeLinkListener<TaskIssueUiData>): JComponent =
  JBPanel<JBPanel<*>>().apply {
    layout = VerticalLayout(6)
    issuesGroup.issues.forEach {
      add(HyperlinkLabel(it.task.taskPath).apply { addHyperlinkListener { _ -> listener.clickedOn(it) } })
    }
  }

@Deprecated("Used only in old navigation model, to be removed with cleanup.")
fun criticalPathHeader(prefix: String, duration: String): JComponent =
  headerLabel("${prefix} determining this build's duration (${duration})")

@Deprecated("Used only in old navigation model, to be removed with cleanup.")
fun headerLabel(text: String): JLabel = JBLabel(text).withFont(JBUI.Fonts.label(13f).asBold()).apply {
  name = "pageHeader"
}

/**
 * Label with auto-wrapping turned on that accepts html text.
 * Used in Build Analyzer to render long multi-line text.
 */
fun htmlTextLabelWithLinesWrap(htmlBodyContent: String): JComponent =
  SwingHelper.createHtmlViewer(true, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, foreground)
  }

fun htmlTextLabelWithFixedLines(htmlBodyContent: String): JComponent =
  SwingHelper.createHtmlViewer(false, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, foreground)
  }
