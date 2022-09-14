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

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.model.PluginDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsPageType
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
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

  private fun createDetailsPage(nodeDescriptor: TasksTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskDetailsNodeDescriptor -> createTaskDetailsPage(nodeDescriptor)
    is PluginDetailsNodeDescriptor -> createPluginDetailsPage(nodeDescriptor)
  }.apply {
    name = nodeDescriptor.pageId.id
  }

  private fun createTaskDetailsPage(descriptor: TaskDetailsNodeDescriptor) = taskDetailsPage(
    descriptor.taskData,
    helpLinkListener = actionHandlers::helpLinkClicked,
    generateReportClickedListener = actionHandlers::generateReportClicked
  )

  private fun createPluginDetailsPage(descriptor: PluginDetailsNodeDescriptor): JComponent {
    fun inlinedTaskInfo(taskUiData: TaskUiData) = JPanel().apply {
      border = JBUI.Borders.emptyTop(5)
      layout = BorderLayout(5, 5)

      val taskInfo = JPanel().apply {
        layout = VerticalLayout(0, SwingConstants.LEFT)
        val taskNavigationLink = HyperlinkLabel(taskUiData.taskPath).apply {
          addHyperlinkListener {
            actionHandlers.tasksDetailsLinkClicked(TasksPageId.task(taskUiData, TasksDataPageModel.Grouping.BY_PLUGIN))
          }
        }
        val info = htmlTextLabelWithFixedLines("""
          Type: ${taskUiData.taskType}<br/>
          Duration: ${taskUiData.executionTime.durationStringHtml()}
          """.trimIndent()
        )
        add(taskNavigationLink)
        add(info)
      }

      val descriptions = JPanel().apply {
        layout = TabularLayout("*")
        taskUiData.issues.forEachIndexed { index, issue ->
          val description = DescriptionWithHelpLinkLabel(issue.explanation, issue.helpLink) {
            actionHandlers.helpLinkClicked(issue.helpLink)
          }
          description.withBorder(JBUI.Borders.emptyTop(5))
          add(description, TabularLayout.Constraint(index, 0))
        }
      }
      add(JLabel(warningIcon()).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.WEST)
      add(taskInfo, BorderLayout.CENTER)
      add(descriptions, BorderLayout.SOUTH)
    }

    return JPanel().apply {
      layout = BorderLayout()
      val tasksNumber = descriptor.filteredTaskNodes.size
      val pluginInfoText = """
        <b>${descriptor.pluginData.name}</b><br/>
        Total duration: ${descriptor.filteredPluginTime.toTimeWithPercentage().durationStringHtml()}<br/>
        Number of tasks: ${tasksNumber.withPluralization("task")}<br/>
        <br/>
      """.trimIndent()
      add(htmlTextLabelWithFixedLines(pluginInfoText), BorderLayout.NORTH)
      add(JPanel().apply {
        name = "plugin-warnings"
        layout = BorderLayout()
        val tasksWithWarnings = descriptor.filteredTaskNodes.filter { it.hasWarning }
        val warningsPanelHeaderHtml =
          "<b>Warnings</b><br/>" +
          if (tasksWithWarnings.isEmpty())
            "No warnings detected for this plugin."
          else
            "${tasksWithWarnings.size.withPluralization("task")} with warnings associated with this plugin.<br/>" +
            if (tasksWithWarnings.size > 10) "Top 10 tasks shown below, you can find the full list in the tree on the left.<br/>"
            else ""
        val warningsPanelHeader = htmlTextLabelWithFixedLines(warningsPanelHeaderHtml)
        val warningsListPanel = JPanel().apply {
          name = "plugin-warnings-list"
          layout = TabularLayout("*")
          tasksWithWarnings.take(10).forEachIndexed { index, task ->
            add(inlinedTaskInfo(task), TabularLayout.Constraint(index, 0))
          }
        }
        add(warningsPanelHeader, BorderLayout.NORTH)
        add(warningsListPanel, BorderLayout.CENTER)
      }, BorderLayout.CENTER)
    }
  }

  private fun Int.withPluralization(base: String): String = "${this} ${StringUtil.pluralize(base, this)}"
}