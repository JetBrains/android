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

import com.android.build.attribution.ui.TreeNodeSelector
import com.android.build.attribution.ui.colorIcon
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.taskIcon
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.ChartBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.panels.TimeDistributionChart.AggregatedChartDataItem
import com.android.build.attribution.ui.panels.TimeDistributionChart.ChartDataItem
import com.android.build.attribution.ui.panels.TimeDistributionChart.SingularChartDataItem
import com.android.build.attribution.ui.panels.TreeLinkListener
import com.android.build.attribution.ui.panels.createIssueTypeListPanel
import com.android.build.attribution.ui.panels.criticalPathHeader
import com.android.build.attribution.ui.panels.headerLabel
import com.android.build.attribution.ui.panels.pluginInfoPanel
import com.android.build.attribution.ui.panels.pluginTasksListPanel
import com.android.build.attribution.ui.panels.taskInfoPanel
import com.intellij.ui.treeStructure.SimpleNode
import java.util.ArrayList
import java.util.HashMap
import javax.swing.Icon
import javax.swing.JComponent

class CriticalPathPluginsRoot(
  private val criticalPathUiData: CriticalPathPluginsUiData,
  parent: SimpleNode,
  private val nodeSelector: TreeNodeSelector
) : AbstractBuildAttributionNode(parent, "Plugins With Critical Path Tasks") {

  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>> = createPluginChartItems(criticalPathUiData)

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(criticalPathUiData.warningCount, criticalPathUiData.infoCount)

  override val timeSuffix: String? = criticalPathUiData.criticalPathDuration.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : ChartBuildAttributionInfoPanel() {
    override fun createChart(): JComponent = TimeDistributionChart(chartItems, null, true)
    override fun createLegend(): JComponent? = null
    override fun createRightInfoPanel(): JComponent? = null
    override fun createHeader(): JComponent = criticalPathHeader("Plugins", criticalPathUiData.criticalPathDuration.durationString())
  }

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = ArrayList<SimpleNode>()
    for (item in chartItems) {
      when (item) {
        is SingularChartDataItem<CriticalPathPluginUiData> ->
          nodes.add(PluginNode(item.underlyingData, chartItems, item, this, nodeSelector))
        is AggregatedChartDataItem<CriticalPathPluginUiData> ->
          item.underlyingData.forEach { nodes.add(PluginNode(it, chartItems, item, this, nodeSelector)) }
      }
    }
    return nodes.toTypedArray()
  }
}


private abstract class ChartElementSelectedPanel(
  private val pluginData: CriticalPathPluginUiData,
  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>>,
  private val selectedChartItem: ChartDataItem<CriticalPathPluginUiData>
) : ChartBuildAttributionInfoPanel() {

  override fun createChart(): JComponent = TimeDistributionChart(chartItems, selectedChartItem, false)

  override fun createLegend(): JComponent? = null

  override fun createHeader(): JComponent = headerLabel(pluginData.name)
}

private class PluginNode(
  private val pluginData: CriticalPathPluginUiData,
  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>>,
  private val selectedChartItem: ChartDataItem<CriticalPathPluginUiData>,
  parent: SimpleNode,
  private val nodeSelector: TreeNodeSelector
) : AbstractBuildAttributionNode(parent, pluginData.name) {

  private val issueRoots = HashMap<TaskIssueType, PluginIssueRootNode>()

  private val issueTypeClickListener = object : TreeLinkListener<TaskIssueType> {
    override fun clickedOn(target: TaskIssueType) {
      issueRoots[target]?.let { nodeSelector.selectNode(it) }
    }
  }

  private val issueClickListener = object : TreeLinkListener<TaskIssueUiData> {
    override fun clickedOn(target: TaskIssueUiData) {
      issueRoots[target.type]?.clickedOn(target)
    }
  }

  override val presentationIcon: Icon? = colorIcon(selectedChartItem.legendColor)

  override val issuesCountsSuffix: String? = issuesCountString(pluginData.warningCount, pluginData.infoCount)

  override val timeSuffix: String? = pluginData.criticalPathDuration.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel =
    object : ChartElementSelectedPanel(pluginData, chartItems, selectedChartItem) {
      override fun createRightInfoPanel(): JComponent = pluginInfoPanel(pluginData, issueTypeClickListener)
    }

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = ArrayList<SimpleNode>()
    nodes.add(PluginTasksRootNode(pluginData, chartItems, selectedChartItem, this, issueClickListener))
    pluginData.issues.forEach { issuesGroup ->
      nodes.add(
        PluginIssueRootNode(issuesGroup, pluginData, this, nodeSelector)
          .also { issueRoots[issuesGroup.type] = it }
      )
    }
    return nodes.toTypedArray()
  }
}

private class PluginTasksRootNode(
  private val pluginData: CriticalPathPluginUiData,
  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>>,
  private val selectedChartItem: ChartDataItem<CriticalPathPluginUiData>,
  parent: SimpleNode,
  private val issueClickListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, "Critical Path Tasks") {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? =
    issuesCountString(pluginData.criticalPathTasks.warningCount, pluginData.criticalPathTasks.infoCount)

  override val timeSuffix: String? = pluginData.criticalPathTasks.criticalPathDuration.durationString()


  override fun createComponent(): AbstractBuildAttributionInfoPanel =
    object : ChartElementSelectedPanel(pluginData, chartItems, selectedChartItem) {
      override fun createRightInfoPanel(): JComponent {
        return pluginTasksListPanel(pluginData)
      }
    }.withPreferredWidth(300)

  override fun buildChildren(): Array<SimpleNode> = pluginData.criticalPathTasks.tasks
    .map { task -> PluginTaskNode(task, this, issueClickListener) }
    .toTypedArray()
}

private class PluginTaskNode(
  private val taskData: TaskUiData,
  parent: SimpleNode,
  private val issueClickListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, taskData.taskPath) {

  override val presentationIcon: Icon? = taskIcon(taskData)

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = taskData.executionTime.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent = headerLabel(taskData.taskPath)

    override fun createBody(): JComponent = taskInfoPanel(taskData, issueClickListener)
  }

  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}

private class PluginIssueRootNode(
  private val issuesGroup: TaskIssuesGroup,
  private val pluginUiData: CriticalPathPluginUiData,
  parent: SimpleNode,
  private val nodeSelector: TreeNodeSelector
) : AbstractBuildAttributionNode(parent, issuesGroup.type.uiName), TreeLinkListener<TaskIssueUiData> {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(issuesGroup.warningCount, issuesGroup.infoCount)

  override val timeSuffix: String? = issuesGroup.timeContribution.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent = headerLabel(pluginUiData.name)

    override fun createBody(): JComponent = createIssueTypeListPanel(issuesGroup, this@PluginIssueRootNode)
  }

  override fun clickedOn(target: TaskIssueUiData) {
    for (child in children) {
      if (child is TaskIssueNode && child.issue === target) {
        nodeSelector.selectNode(child)
        return
      }
    }
  }

  override fun buildChildren(): Array<SimpleNode> = issuesGroup.issues
    .map { issue -> TaskIssueNode(issue, this) }
    .toTypedArray()
}
