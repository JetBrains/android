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

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.colorIcon
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.mergedIcon
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.CRITICAL_PATH_LINK
import com.android.build.attribution.ui.panels.ChartBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.panels.TreeLinkListener
import com.android.build.attribution.ui.panels.criticalPathHeader
import com.android.build.attribution.ui.panels.headerLabel
import com.android.build.attribution.ui.panels.taskInfoPanel
import com.android.build.attribution.ui.taskIcon
import com.android.utils.HtmlBuilder
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

class CriticalPathTasksRoot(
  private val data: CriticalPathTasksUiData,
  parent: ControllersAwareBuildAttributionNode,
  private val taskIssueLinkListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, "Tasks determining this build's duration") {
  private val chartItems: List<TimeDistributionChart.ChartDataItem<TaskUiData>> = createTaskChartItems(data)

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(data.warningCount, data.infoCount)

  override val timeSuffix: String? = data.criticalPathDuration.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = mutableListOf<SimpleNode>()
    for (item in chartItems) {
      when (item) {
        is TimeDistributionChart.SingularChartDataItem<TaskUiData> ->
          nodes.add(TaskNode(item.underlyingData, chartItems, item, this, taskIssueLinkListener))
        is TimeDistributionChart.AggregatedChartDataItem<TaskUiData> ->
          item.underlyingData.forEach { taskData -> nodes.add(TaskNode(taskData, chartItems, item, this, taskIssueLinkListener)) }
      }
    }
    return nodes.toTypedArray()
  }

  override fun createComponent(): AbstractBuildAttributionInfoPanel {
    return object : ChartBuildAttributionInfoPanel() {

      override fun createHeader(): JComponent {
        return criticalPathHeader("Tasks", data.criticalPathDuration.durationString())
      }

      override fun createChart(): JComponent {
        return TimeDistributionChart(chartItems, null, true)
      }

      override fun createLegend(): JComponent {
        return CriticalPathChartLegend.createTasksLegendPanel()
      }

      override fun createDescription(): JComponent {
        val text = HtmlBuilder()
          .openHtmlBody()
          .add("These tasks belong to a group of sequentially executed tasks that has the largest impact on this build's duration.")
          .newline()
          .add("Addressing this group provides the greatest likelihood of reducing the overall build duration.")
          .closeHtmlBody()
        return DescriptionWithHelpLinkLabel(text.html, CRITICAL_PATH_LINK, analytics)
      }

      override fun createRightInfoPanel(): JComponent? {
        return null
      }
    }
  }
}

private class TaskNode(
  private val taskData: TaskUiData,
  private val chartItems: List<TimeDistributionChart.ChartDataItem<TaskUiData>>,
  private val selectedChartItem: TimeDistributionChart.ChartDataItem<TaskUiData>,
  parent: CriticalPathTasksRoot,
  private val taskIssueLinkListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, taskData.taskPath) {

  override val presentationIcon: Icon? = mergedIcon(taskIcon(taskData), colorIcon(selectedChartItem.legendColor.baseColor))

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = taskData.executionTime.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASK_PAGE

  override fun createComponent(): AbstractBuildAttributionInfoPanel {
    return object : ChartBuildAttributionInfoPanel() {
      override fun createChart(): JComponent {
        return TimeDistributionChart(chartItems, selectedChartItem, false)
      }

      override fun createLegend(): JComponent {
        return CriticalPathChartLegend.createTasksLegendPanel()
      }

      override fun createDescription(): JComponent? = null

      override fun createRightInfoPanel(): JComponent {
        return taskInfoPanel(taskData, taskIssueLinkListener)
      }

      override fun createHeader(): JComponent {
        return headerLabel(taskData.taskPath)
      }
    }
  }

  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}
