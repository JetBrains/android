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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issueIcon
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.TaskIssueInfoPanel
import com.android.build.attribution.ui.panels.headerLabel
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

class TaskIssuesRoot(
  val issuesGroup: TaskIssuesGroup,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, issuesGroup.type.uiName) {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(issuesGroup.warningCount, issuesGroup.infoCount)

  override val timeSuffix: String? = null

  override val pageType = when (issuesGroup.type) {
    TaskIssueType.ALWAYS_RUN_TASKS -> BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> BuildAttributionUiEvent.Page.PageType.TASK_SETUP_ISSUE_ROOT
  }

  fun findNodeForIssue(issue: TaskIssueUiData): TaskIssueNode? =
    children.asSequence().filterIsInstance<TaskIssueNode>().first { it.issue == issue }

  override fun buildChildren(): Array<SimpleNode> {
    return issuesGroup.issues
      .map { issue ->
        object : TaskIssueNode(issue, this) {
          override val pageType = issue.toAnalyticsPageType()
        }
      }
      .toTypedArray()
  }

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent {
      return headerLabel(issuesGroup.type.uiName)
    }

    override fun createBody(): JComponent {
      val listPanel = JBPanel<JBPanel<*>>(VerticalLayout(6))
      children.forEach {
        val text = (it as? AbstractBuildAttributionNode)?.nodeName ?: it.name
        val link = HyperlinkLabel(text)
        link.addHyperlinkListener { _ -> nodeSelector.selectNode(it) }
        listPanel.add(link)
      }
      return listPanel
    }
  }
}

abstract class TaskIssueNode(
  val issue: TaskIssueUiData,
  parent: AbstractBuildAttributionNode
) : AbstractBuildAttributionNode(parent, issue.task.taskPath) {

  override val presentationIcon: Icon? = issueIcon(issue.type)

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = issue.task.executionTime.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel {
    return object : AbstractBuildAttributionInfoPanel() {
      override fun createHeader(): JComponent {
        return headerLabel(issue.task.taskPath)
      }

      override fun createBody(): JComponent {
        return TaskIssueInfoPanel(issue, issueReporter, analytics)
      }
    }
  }

  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}

private fun TaskIssueUiData.toAnalyticsPageType(): BuildAttributionUiEvent.Page.PageType = when {
  this is TaskIssueUiDataContainer.AlwaysRunNoOutputIssue -> BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE
  this is TaskIssueUiDataContainer.AlwaysRunUpToDateOverride -> BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
  this is TaskIssueUiDataContainer.TaskSetupIssue -> BuildAttributionUiEvent.Page.PageType.TASK_SETUP_ISSUE_PAGE
  else -> BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
}
