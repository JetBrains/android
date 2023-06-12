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

import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.displayName
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.helpIcon
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.percentageStringHtml
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warnIconHtml
import com.android.build.attribution.ui.withPluralization
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.HtmlBuilder
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

fun taskDetailsPage(
  taskData: TaskUiData,
  actionHandlers: ViewActionHandlers
): JComponent = JPanel().apply {
  layout = BoxLayout(this, BoxLayout.Y_AXIS)
  val linksHandler = HtmlLinksHandler(actionHandlers)
  val detailsPanelHtml = taskDetailsPanelHtml(taskData, actionHandlers, linksHandler)
  val htmlLabel = htmlTextLabelWithFixedLines(detailsPanelHtml, linksHandler)
  htmlLabel.alignmentX = 0f
  add(htmlLabel)
}

private const val NO_PLUGIN_INFO_HELP_TEXT =
  "Gradle did not provide plugin information for this task due to Configuration cache being enabled and its entry being reused."

private fun pluginNameHtml(taskData: TaskUiData) = when {
  taskData.pluginUnknownBecauseOfCC -> "N/A ${helpIcon(NO_PLUGIN_INFO_HELP_TEXT)}"
  else -> taskData.pluginName
}

fun taskDetailsPanelHtml(
  taskData: TaskUiData,
  actionHandlers: ViewActionHandlers,
  linksHandler: HtmlLinksHandler
): String {
  return HtmlBuilder().apply {
    addBold(taskData.taskPath).newline()
    when {
      taskData.onLogicalCriticalPath -> this
        .add("This task frequently determines build duration because of dependencies").newline()
        .add("between its inputs/outputs and other tasks.").newline()
      taskData.onExtendedCriticalPath -> this
        .add("This task occasionally determines build duration because of parallelism constraints").newline()
        .add("introduced by number of cores or other tasks in the same module.").newline()
    }
    newline()
    addBold("Duration:").addHtml("  ${taskData.executionTime.durationStringHtml()} / ${taskData.executionTime.percentageStringHtml()}").newline()
    add("Sub-project: ${taskData.module}").newline()
    addHtml("Plugin: ${pluginNameHtml(taskData)}").newline()
    add("Type: ${taskData.taskType}").newline()
    if (StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.get()) {
      val taskCategories = listOf(taskData.primaryTaskCategory) + taskData.secondaryTaskCategories
      add("Task Execution Categories: ${taskCategories.joinToString { it.displayName() }}").newline()
    }
    newline()
    createWarningsSection(taskData, actionHandlers, linksHandler)
    createReasonsSection(taskData.reasonsToRun)
  }.html
}

private fun HtmlBuilder.createWarningsSection(
  taskData: TaskUiData,
  actionHandlers: ViewActionHandlers,
  linksHandler: HtmlLinksHandler
) {
  addBold("Warnings").newline()
  if (taskData.issues.isEmpty() && taskData.relatedTaskCategoryIssues.isEmpty()) {
    add("No warnings found").newline()
    newline()
  }
  else {
    if (taskData.sourceType != PluginSourceType.BUILD_SCRIPT && taskData.issues.isNotEmpty()) {
      val generateReportLink = linksHandler.actionLink("Generate report", "generateReport") {
        actionHandlers.generateReportClicked(taskData)
      }
      addHtml("Consider filing a bug to report this issue to the plugin developer. $generateReportLink")
    }
    beginTable()
    if (taskData.relatedTaskCategoryIssues.isNotEmpty()) {
      val redirectLink = linksHandler.actionLink(taskData.primaryTaskCategory.displayName(), taskData.primaryTaskCategory.name) {
        actionHandlers.redirectToTaskCategoryWarningsPage(taskData.primaryTaskCategory)
      }
      addTableRow(warnIconHtml, "This task is impacted by ${taskData.relatedTaskCategoryIssues.size.withPluralization("issue")} found in the $redirectLink category.")
    }
    taskData.issues.forEach { issue ->
      val description = "${issue.explanation}\n${linksHandler.externalLink("Learn more", issue.helpLink)}"
      addTableRow(warnIconHtml, "<B>${issue.type.uiName}</B>")
      addTableRow("", description.replace("\n", "<BR/>"))
      addTableRow("", "<B>Recommendation:</B> ${issue.buildSrcRecommendation.replace("\n", "<BR/>")}")
    }
    endTable()
  }
}

private fun HtmlBuilder.createReasonsSection(reasons: List<String>) {
  addBold("Reason task ran").newline()
  if (reasons.isEmpty()) {
    add("No info")
  }
  else {
    reasons.forEach {
      addHtml(it.replace("\n", "<BR/>")).newline()
    }
  }
}


