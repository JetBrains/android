/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.createTaskCategoryIssueMessage
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.model.EntryDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsPageType
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warnIconHtml
import com.android.build.attribution.ui.withPluralization
import com.android.utils.HtmlBuilder
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * This class creates task detail pages from the node provided.
 */
class TaskViewDetailPagesFactory(
  val model: TasksDataPageModel,
  val actionHandlers: ViewActionHandlers
) {

  fun createDetailsPage(pageId: TasksPageId): JComponent =
    if (pageId.pageType == TaskDetailsPageType.EMPTY_SELECTION) {
      createEmptyPage()
    }
    else {
      model.getNodeDescriptorById(pageId)?.let { nodeDescriptor ->
        createDetailsPage(nodeDescriptor)
      } ?: JPanel()
    }

  private fun createEmptyPage() = JPanel().apply {
    layout = BorderLayout()
    name = "empty-details"
    val messageLabel = JLabel("Select page for details").apply {
      verticalAlignment = SwingConstants.CENTER
      horizontalAlignment = SwingConstants.CENTER
    }
    add(messageLabel, BorderLayout.CENTER)
  }

  fun createDetailsPage(nodeDescriptor: TasksTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskDetailsNodeDescriptor -> createTaskDetailsPage(nodeDescriptor)
    is EntryDetailsNodeDescriptor -> createEntryDetailsPage(nodeDescriptor)
  }.apply {
    name = nodeDescriptor.pageId.id
  }

  private fun createTaskDetailsPage(descriptor: TaskDetailsNodeDescriptor) = taskDetailsPage(descriptor.taskData, actionHandlers)

  private fun createEntryDetailsPage(descriptor: EntryDetailsNodeDescriptor): JComponent {
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      val linksHandler = HtmlLinksHandler(actionHandlers)
      val detailsPanelHtml = entryDetailsHtml(descriptor, linksHandler)
      val htmlLabel = htmlTextLabelWithFixedLines(detailsPanelHtml, linksHandler)
      htmlLabel.alignmentX = 0f
      add(htmlLabel)
    }
  }

  fun entryDetailsHtml(
    descriptor: EntryDetailsNodeDescriptor,
    linksHandler: HtmlLinksHandler
  ): String {
    return HtmlBuilder().apply {
      val filteredTasksNumber = descriptor.filteredTaskNodes.size
      val filteredTasksWithWarnings = descriptor.filteredTaskNodes.filter { it.hasWarning }
      addBold(descriptor.entryData.name).newline()
      if (descriptor.entryData is CriticalPathTaskCategoryUiData) add(descriptor.entryData.taskCategoryDescription).newline().newline()
      add("Total duration: ").addHtml(descriptor.filteredEntryTime.toTimeWithPercentage().durationStringHtml()).newline()
      //TODO (b/240926892): these are filtered tasks, should make it clear for the user.
      add("Number of tasks: ${filteredTasksNumber.withPluralization("task")}").newline()
      newline()

      if (descriptor.entryData is CriticalPathTaskCategoryUiData) {
        val taskCategoryInfos = descriptor.entryData.taskCategoryInfos
        if (taskCategoryInfos.isNotEmpty()) {
          createTaskCategoryIssueMessage(taskCategoryInfos, linksHandler, actionHandlers)
          newline()
        }
      }

      addBold("Warnings").newline()
      var warningCount = filteredTasksWithWarnings.size
      if (descriptor.entryData is CriticalPathTaskCategoryUiData) warningCount += descriptor.entryData.taskCategoryWarnings.size
      if (warningCount == 0) {
        //TODO (b/240926892): same here, these are filtered, need to make it clear on UI
        if (descriptor.entryData is CriticalPathTaskCategoryUiData) {
          add("No warnings detected for ${descriptor.entryData.name} category.")
        } else {
          add("No warnings detected for this plugin.")
        }
      } else {
        if (descriptor.entryData is CriticalPathTaskCategoryUiData) {
          add("${warningCount.withPluralization("warning")} associated with ${descriptor.entryData.name} category.").newline()
        } else {
          add("${warningCount.withPluralization("task")} with warnings associated with this plugin.").newline()
        }
        if (warningCount > 10) {
          add("Top 10 warnings shown below, you can find the full list in the tree on the left.").newline()
        }
        if (descriptor.entryData is CriticalPathTaskCategoryUiData) {
          if (descriptor.entryData.taskCategoryWarnings.isNotEmpty()) {
            createTaskCategoryIssueMessage(descriptor.entryData.taskCategoryWarnings, linksHandler, actionHandlers)
            warningCount -= descriptor.entryData.taskCategoryWarnings.size
          }
        }
        filteredTasksWithWarnings.take(minOf(warningCount, 10)).forEach { task ->
          val linkToTask = linksHandler.actionLink(task.taskPath, task.taskPath) {
            actionHandlers.tasksDetailsLinkClicked(TasksPageId.task(task, descriptor.entryData.modelGrouping))
          }
          beginTable()
          addTableRow(warnIconHtml, linkToTask)
          addTableRow("", "Type: ${task.taskType}<BR/>Duration: ${task.executionTime.durationStringHtml()}")
          endTable()
          task.issues.forEach { issue ->
            val description = "${issue.explanation}\n${linksHandler.externalLink("Learn more", issue.helpLink)}".replace("\n", "<BR/>")
            addHtml(description).newline()
          }
        }
      }
    }.html
  }


}