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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.AlwaysRunNoOutputIssue
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.AlwaysRunUpToDateOverride
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.TaskSetupIssue
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TaskDetailsPageType
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.TasksDataPageModel.Grouping
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType

class BuildAnalyzerViewController(
  val model: BuildAnalyzerViewModel,
  private val analytics: BuildAttributionUiAnalytics,
  private val issueReporter: TaskIssueReporter
) : ViewActionHandlers {

  init {
    val pageId = model.selectedData.toAnalyticsPage()
    analytics.initFirstPage(pageId)
  }

  override fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet) {
    model.selectedData = newSelectedData
    val analyticsPageId = newSelectedData.toAnalyticsPage()
    analytics.pageChange(analyticsPageId, BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
  }

  override fun changeViewToTasksLinkClicked(targetGrouping: Grouping) {
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    model.tasksPageModel.selectGrouping(targetGrouping)
    // TODO (b/154988129): what metrics to track on such action?
  }

  override fun changeViewToWarningsLinkClicked() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
    // TODO (b/154988129): what metrics to track on such action?
  }

  override fun tasksGroupingSelectionUpdated(grouping: Grouping) {
    model.tasksPageModel.selectGrouping(grouping)
    // TODO (b/154988129): what metrics to track on such action?
  }

  override fun tasksTreeNodeSelected(tasksTreeNode: TasksTreeNode) {
    // Update selection in the model.
    model.tasksPageModel.selectNode(tasksTreeNode)
    // Track page change in analytics.
    val pageId = tasksTreeNode.descriptor.pageId.toAnalyticsPage()
    analytics.pageChange(pageId, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun tasksDetailsLinkClicked(taskPageId: TasksPageId) {
    // Make sure tasks page open.
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    // Update selection in the tasks page model.
    model.tasksPageModel.selectPageById(taskPageId)
    // Track page change in analytics.
    val pageId = taskPageId.toAnalyticsPage()
    analytics.pageChange(pageId, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun warningsTreeNodeSelected(warningTreeNode: WarningsTreeNode) {
    // Update selection in the model.
    model.warningsPageModel.selectNode(warningTreeNode)
    // Track page change in analytics.
    val pageId = warningTreeNode.descriptor.toAnalyticsPage()
    analytics.pageChange(pageId, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun helpLinkClicked() {
    //TODO (b/154988129): currently it is tracked only by currently opened page.
    // If we have more links on the page it will not be tracked properly.
    // Change to track the link context.
    analytics.helpLinkClicked()
  }

  override fun generateReportClicked(taskData: TaskUiData) {
    analytics.bugReportLinkClicked()
    issueReporter.reportIssue(taskData)
  }

  private fun BuildAnalyzerViewModel.DataSet.toAnalyticsPage(): BuildAttributionUiAnalytics.AnalyticsPageId {
    val type = when (this) {
      BuildAnalyzerViewModel.DataSet.OVERVIEW -> PageType.BUILD_SUMMARY
      BuildAnalyzerViewModel.DataSet.TASKS -> PageType.CRITICAL_PATH_TASKS_ROOT
      BuildAnalyzerViewModel.DataSet.WARNINGS -> PageType.WARNINGS_ROOT
    }
    return BuildAttributionUiAnalytics.AnalyticsPageId(type, this.name)
  }

  private fun TasksPageId.toAnalyticsPage(): BuildAttributionUiAnalytics.AnalyticsPageId {
    val type: PageType = when {
      grouping == Grouping.UNGROUPED && pageType == TaskDetailsPageType.TASK_DETAILS -> PageType.CRITICAL_PATH_TASK_PAGE
      grouping == Grouping.BY_PLUGIN && pageType == TaskDetailsPageType.TASK_DETAILS -> PageType.PLUGIN_CRITICAL_PATH_TASK_PAGE
      grouping == Grouping.BY_PLUGIN && pageType == TaskDetailsPageType.PLUGIN_DETAILS -> PageType.PLUGIN_PAGE
      else -> PageType.UNKNOWN_PAGE
    }
    return BuildAttributionUiAnalytics.AnalyticsPageId(type, this.id)
  }

  private fun WarningsTreePresentableNodeDescriptor.toAnalyticsPage(): BuildAttributionUiAnalytics.AnalyticsPageId {
    val type: PageType = when (this) {
      is TaskWarningTypeNodeDescriptor -> this.warningTypeData.type.toAnalyticsType()
      is TaskWarningDetailsNodeDescriptor -> this.issueData.toAnalyticsType()
      is AnnotationProcessorsRootNodeDescriptor -> PageType.ANNOTATION_PROCESSOR_PAGE
      is AnnotationProcessorDetailsNodeDescriptor -> PageType.ANNOTATION_PROCESSORS_ROOT
    }
    return BuildAttributionUiAnalytics.AnalyticsPageId(type, pageId.id)
  }

  private fun TaskIssueType.toAnalyticsType(): PageType = when (this) {
    TaskIssueType.ALWAYS_RUN_TASKS -> PageType.ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> PageType.TASK_SETUP_ISSUE_ROOT
  }

  private fun TaskIssueUiData.toAnalyticsType(): PageType = when (this) {
    is TaskSetupIssue -> PageType.TASK_SETUP_ISSUE_PAGE
    is AlwaysRunNoOutputIssue -> PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE
    is AlwaysRunUpToDateOverride -> PageType.ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
    else -> PageType.UNKNOWN_PAGE
  }
}
