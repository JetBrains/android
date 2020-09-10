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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel.Grouping
import com.android.build.attribution.ui.model.TasksFilter
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.WarningsFilter
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.tools.idea.memorysettings.MemorySettingsConfigurable
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

class BuildAnalyzerViewController(
  val model: BuildAnalyzerViewModel,
  private val project: Project,
  private val analytics: BuildAttributionUiAnalytics,
  private val issueReporter: TaskIssueReporter
) : ViewActionHandlers {

  init {
    analytics.initFirstPage(model)
  }

  override fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = newSelectedData
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.DATA_VIEW_COMBO_SELECTED)
  }

  override fun changeViewToTasksLinkClicked(targetGrouping: Grouping) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    model.tasksPageModel.selectGrouping(targetGrouping)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun changeViewToWarningsLinkClicked() {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun tasksGroupingSelectionUpdated(grouping: Grouping) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.tasksPageModel.selectGrouping(grouping)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.GROUPING_CHANGED)
  }

  override fun tasksTreeNodeSelected(tasksTreeNode: TasksTreeNode?) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.tasksPageModel.selectNode(tasksTreeNode)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun tasksDetailsLinkClicked(taskPageId: TasksPageId) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    // Make sure tasks page open.
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    // Update selection in the tasks page model.
    model.tasksPageModel.selectPageById(taskPageId)
    // Track page change in analytics.
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun warningsTreeNodeSelected(warningTreeNode: WarningsTreeNode?) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    // Update selection in the model.
    model.warningsPageModel.selectNode(warningTreeNode)
    // Track page change in analytics.
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun helpLinkClicked(linkTarget: BuildAnalyzerBrowserLinks) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    analytics.helpLinkClicked(currentAnalyticsPage, linkTarget)
  }

  override fun generateReportClicked(taskData: TaskUiData) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    analytics.bugReportLinkClicked(currentAnalyticsPage)
    issueReporter.reportIssue(taskData)
  }

  override fun openMemorySettings() {
    analytics.memorySettingsOpened()
    ShowSettingsUtil.getInstance().showSettingsDialog(project, MemorySettingsConfigurable::class.java)
  }

  override fun applyTasksFilter(filter: TasksFilter) {
    model.tasksPageModel.applyFilter(filter)
    analytics.tasksFilterApplied(filter)
  }

  override fun applyWarningsFilter(filter: WarningsFilter) {
    model.warningsPageModel.filter = filter
    analytics.warningsFilterApplied(filter)
  }

  override fun warningsGroupingSelectionUpdated(groupByPlugin: Boolean) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.warningsPageModel.groupByPlugin = groupByPlugin
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.GROUPING_CHANGED)
  }
}
