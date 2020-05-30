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
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.model.PluginDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.taskDetailsPanel
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * This class creates task detail pages from the node provided.
 */
class TaskViewDetailPagesFactory(
  val actionHandlers: ViewActionHandlers
) {

  fun createDetailsPage(nodeDescriptor: TasksTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskDetailsNodeDescriptor -> createTaskDetailsPage(nodeDescriptor)
    is PluginDetailsNodeDescriptor -> createPluginDetailsPage(nodeDescriptor)
  }

  private fun createTaskDetailsPage(descriptor: TaskDetailsNodeDescriptor) = JPanel().apply {
    name = descriptor.pageId.toString()
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    alignmentX = Component.LEFT_ALIGNMENT
    add(JBLabel(descriptor.taskData.taskPath).withFont(JBUI.Fonts.label().asBold()))
    add(taskDetailsPanel(
      descriptor.taskData,
      helpLinkListener = actionHandlers::helpLinkClicked,
      generateReportClickedListener = actionHandlers::generateReportClicked
    ))
  }

  private fun createPluginDetailsPage(descriptor: PluginDetailsNodeDescriptor): JComponent {
    fun inlinedTaskInfo(taskUiData: TaskUiData) = JPanel().apply {
      border = JBUI.Borders.emptyTop(5)
      alignmentX = Component.LEFT_ALIGNMENT
      layout = TabularLayout("Fit,Fit,*")
      add(JBLabel(warningIcon()).withBorder(JBUI.Borders.emptyRight(5)), TabularLayout.Constraint(0, 0))
      var row = 0
      fun addRow(component: Component, col: Int = 1, colSpan: Int = 1) {
        add(component, TabularLayout.Constraint(row++, col, colSpan))
      }
      addRow(HyperlinkLabel(taskUiData.taskPath).apply {
        addHyperlinkListener { actionHandlers.tasksDetailsLinkClicked(TasksPageId.task(taskUiData, TasksDataPageModel.Grouping.BY_PLUGIN)) }
      })
      addRow(JLabel("Type: ${taskUiData.taskType}"))
      addRow(JLabel("Duration: ${taskUiData.executionTime.durationString()}"))
      taskUiData.issues.forEach { issue ->
        val description = DescriptionWithHelpLinkLabel(issue.explanation, issue.helpLink) { actionHandlers.helpLinkClicked(issue.helpLink) }
        description.withBorder(JBUI.Borders.emptyTop(5))
        addRow(description, 0, 3)
      }
    }

    return JPanel().apply {
      name = descriptor.pageId.toString()
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentX = Component.LEFT_ALIGNMENT
      add(JBLabel(descriptor.pluginData.name).withFont(JBUI.Fonts.label().asBold()))
      add(JBLabel("Total duration: ${descriptor.pluginData.criticalPathDuration.durationString()}"))
      val tasksNumber = descriptor.pluginData.criticalPathTasks.size
      add(JBLabel("Number of tasks: $tasksNumber ${StringUtil.pluralize("task", tasksNumber)}"))
      add(JBLabel("Warnings").withFont(JBUI.Fonts.label().asBold()).withBorder(JBUI.Borders.emptyTop(20)))
      descriptor.pluginData.criticalPathTasks.tasks.filter { it.hasWarning }.let {
        if (it.isEmpty()) {
          add(JBLabel("No warnings detected for this plugin."))
        }
        else {
          it.forEach { add(inlinedTaskInfo(it)) }
        }
      }
    }
  }
}