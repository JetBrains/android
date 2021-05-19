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
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.htmlTextLabelWithLinesWrap
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.percentageStringHtml
import com.android.build.attribution.ui.warningIcon
import com.android.build.attribution.ui.wrapPathToSpans
import com.android.tools.adtui.TabularLayout
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

fun taskDetailsPage(
  taskData: TaskUiData,
  helpLinkListener: (BuildAnalyzerBrowserLinks) -> Unit,
  generateReportClickedListener: (TaskUiData) -> Unit
): JPanel = JPanel().apply {
  layout = BorderLayout()
  add(htmlTextLabelWithFixedLines("<b>${taskData.taskPath}</b>"), BorderLayout.NORTH)
  add(taskDetailsPanel( taskData, helpLinkListener, generateReportClickedListener), BorderLayout.CENTER)
}

private fun taskDetailsPanel(
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
      <b>Duration:</b>  ${taskData.executionTime.durationStringHtml()} / ${taskData.executionTime.percentageStringHtml()}<br/>
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

private fun generateReportLinkLabel(
  taskData: TaskUiData,
  generateReportClicked: (TaskUiData) -> Unit
): JComponent = JPanel().apply {
  layout = FlowLayout(FlowLayout.LEFT, 0, 0)
  add(JLabel("Consider filing a bug to report this issue to the plugin developer. "))
  val link = HyperlinkLabel("Generate report")
  link.addHyperlinkListener { generateReportClicked(taskData) }
  add(link)
}

private fun createReasonsText(reasons: List<String>): String = if (reasons.isEmpty()) {
  "No info"
}
else {
  reasons.joinToString(separator = "<br/>") { wrapPathToSpans(it).replace("\n", "<br>") }
}

private fun horizontalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredHeight(1)
  .withMaximumHeight(1)
  .withMinimumHeight(1)

