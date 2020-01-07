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

import com.android.build.attribution.ui.colorIcon
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.CRITICAL_PATH_LINK
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
import com.android.build.attribution.ui.taskIcon
import com.android.utils.HtmlBuilder
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import java.util.ArrayList
import java.util.HashMap
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkListener

class CriticalPathPluginsRoot(
  private val criticalPathUiData: CriticalPathPluginsUiData,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Plugins with tasks determining this build's duration") {

  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>> = createPluginChartItems(criticalPathUiData)

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(criticalPathUiData.warningCount, criticalPathUiData.infoCount)

  override val timeSuffix: String? = criticalPathUiData.criticalPathDuration.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.PLUGINS_ROOT

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : ChartBuildAttributionInfoPanel() {
    override fun createChart(): JComponent = TimeDistributionChart(chartItems, null, true)
    override fun createLegend(): JComponent? = null
    override fun createDescription(): JComponent {
      val text = HtmlBuilder()
        .openHtmlBody()
        .add(
          "These tasks, grouped by plugin, belong to a group of sequentially executed tasks that has the largest impact on this build's duration.")
        .newline()
        .add("Addressing this group provides the greatest likelihood of reducing the overall build duration.")
        .newline()
        .addLink("Learn more", CRITICAL_PATH_LINK)
        .closeHtmlBody()
      return object : JBLabel(text.html) {
        override fun createHyperlinkListener(): HyperlinkListener {
          val hyperlinkListener = super.createHyperlinkListener()
          return HyperlinkListener { e ->
            analytics.helpLinkClicked()
            hyperlinkListener.hyperlinkUpdate(e)
          }
        }
      }
        .setAllowAutoWrapping(true)
        .setCopyable(true)
    }

    override fun createRightInfoPanel(): JComponent? = null
    override fun createHeader(): JComponent =
      criticalPathHeader("Plugins with tasks", criticalPathUiData.criticalPathDuration.durationString())
  }

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = ArrayList<SimpleNode>()
    for (item in chartItems) {
      when (item) {
        is SingularChartDataItem<CriticalPathPluginUiData> ->
          nodes.add(PluginNode(item.underlyingData, chartItems, item, this))
        is AggregatedChartDataItem<CriticalPathPluginUiData> ->
          item.underlyingData.forEach { nodes.add(PluginNode(it, chartItems, item, this)) }
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

  override fun createDescription(): JComponent? = null

  override fun createHeader(): JComponent = headerLabel(pluginData.name)
}

private class PluginNode(
  private val pluginData: CriticalPathPluginUiData,
  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>>,
  private val selectedChartItem: ChartDataItem<CriticalPathPluginUiData>,
  parent: CriticalPathPluginsRoot
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

  override val presentationIcon: Icon? = colorIcon(selectedChartItem.legendColor.baseColor)

  override val issuesCountsSuffix: String? = issuesCountString(pluginData.warningCount, pluginData.infoCount)

  override val timeSuffix: String? = pluginData.criticalPathDuration.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.PLUGIN_PAGE

  override fun createComponent(): AbstractBuildAttributionInfoPanel =
    object : ChartElementSelectedPanel(pluginData, chartItems, selectedChartItem) {
      override fun createRightInfoPanel(): JComponent = pluginInfoPanel(pluginData, issueTypeClickListener, analytics)
    }

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = ArrayList<SimpleNode>()
    nodes.add(PluginTasksRootNode(pluginData, chartItems, selectedChartItem, this, issueClickListener))
    nodes.add(PluginIssuesRootNode(pluginData, this))
    return nodes.toTypedArray()
  }

  fun registerIssueRoot(pluginIssueRoot: PluginIssueRootNode) {
    issueRoots[pluginIssueRoot.issuesGroup.type] = pluginIssueRoot
  }
}

private class PluginTasksRootNode(
  private val pluginData: CriticalPathPluginUiData,
  private val chartItems: List<ChartDataItem<CriticalPathPluginUiData>>,
  private val selectedChartItem: ChartDataItem<CriticalPathPluginUiData>,
  parent: PluginNode,
  private val issueClickListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, "Tasks determining this build's duration") {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? =
    issuesCountString(pluginData.criticalPathTasks.warningCount, pluginData.criticalPathTasks.infoCount)

  override val timeSuffix: String? = pluginData.criticalPathTasks.criticalPathDuration.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.PLUGIN_CRITICAL_PATH_TASKS_ROOT

  override fun createComponent(): AbstractBuildAttributionInfoPanel =
    object : ChartElementSelectedPanel(pluginData, chartItems, selectedChartItem) {
      override fun createRightInfoPanel(): JComponent {
        return pluginTasksListPanel(pluginData, analytics)
      }

      override fun createDescription(): JComponent? = null
    }
      .withPreferredWidth(300)

  override fun buildChildren(): Array<SimpleNode> = pluginData.criticalPathTasks.tasks
    .map { task -> PluginTaskNode(task, this, issueClickListener) }
    .toTypedArray()
}

private class PluginTaskNode(
  private val taskData: TaskUiData,
  parent: PluginTasksRootNode,
  private val issueClickListener: TreeLinkListener<TaskIssueUiData>
) : AbstractBuildAttributionNode(parent, taskData.taskPath) {

  override val presentationIcon: Icon? = taskIcon(taskData)

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = taskData.executionTime.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.PLUGIN_CRITICAL_PATH_TASK_PAGE

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent = headerLabel(taskData.taskPath)

    override fun createBody(): JComponent = taskInfoPanel(taskData, issueClickListener)
  }
    .withPreferredWidth(350)


  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}

private class PluginIssuesRootNode(
  private val pluginUiData: CriticalPathPluginUiData,
  private val parentNode: PluginNode
) : AbstractBuildAttributionNode(parentNode, "Warnings (${pluginUiData.warningCount})") {
  //TODO mlazeba change to new type when added and merged b/144767316
  override val pageType = BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
  override val presentationIcon: Icon? = null
  override val issuesCountsSuffix: String? = null
  override val timeSuffix: String? = null

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent = headerLabel("${pluginUiData.name} warnings")

    override fun createBody(): JComponent {
      val listPanel = JBPanel<JBPanel<*>>(VerticalLayout(6))
      val totalWarningsCount = pluginUiData.warningCount
      listPanel.add(JBLabel().apply {
        text = if (children.isEmpty())
          "No warnings detected for this build."
        else
          "$totalWarningsCount ${StringUtil.pluralize("warning", totalWarningsCount)} " +
          "of the following ${StringUtil.pluralize("type", children.size)} were detected for this build."
        setAllowAutoWrapping(true)
        setCopyable(true)
      })
      children.forEach {
        if (it is AbstractBuildAttributionNode) {
          val name = it.nodeName
          val link = HyperlinkLabel("${name} (${it.issuesCountsSuffix})")
          link.addHyperlinkListener { _ -> nodeSelector.selectNode(it) }
          listPanel.add(link)
        }
      }
      return listPanel
    }
  }

  override fun buildChildren(): Array<SimpleNode> {
    return pluginUiData.issues
      .map { issuesGroup ->
        PluginIssueRootNode(issuesGroup, pluginUiData, this)
          .also { parentNode.registerIssueRoot(it) }
      }
      .toTypedArray()
  }
}

private class PluginIssueRootNode(
  val issuesGroup: TaskIssuesGroup,
  private val pluginUiData: CriticalPathPluginUiData,
  parent: PluginIssuesRootNode
) : AbstractBuildAttributionNode(parent, issuesGroup.type.uiName), TreeLinkListener<TaskIssueUiData> {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(issuesGroup.warningCount, issuesGroup.infoCount)

  override val timeSuffix: String? = issuesGroup.timeContribution.durationString()

  override val pageType = when (issuesGroup.type) {
    TaskIssueType.ALWAYS_RUN_TASKS -> BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> BuildAttributionUiEvent.Page.PageType.PLUGIN_TASK_SETUP_ISSUE_ROOT
  }

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
    .map { issue ->
      object : TaskIssueNode(issue, this) {
        override val pageType = issue.toAnalyticsPageType()
      }
    }
    .toTypedArray()

}

private fun TaskIssueUiData.toAnalyticsPageType(): BuildAttributionUiEvent.Page.PageType = when {
  this is TaskIssueUiDataContainer.AlwaysRunNoOutputIssue -> BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
  this is TaskIssueUiDataContainer.AlwaysRunUpToDateOverride -> BuildAttributionUiEvent.Page.PageType.PLUGIN_ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
  this is TaskIssueUiDataContainer.TaskSetupIssue -> BuildAttributionUiEvent.Page.PageType.PLUGIN_TASK_SETUP_ISSUE_PAGE
  else -> BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
}
