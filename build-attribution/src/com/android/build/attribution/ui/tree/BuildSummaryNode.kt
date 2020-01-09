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

import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.ChartBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.panels.CriticalPathChartLegend.categoricalGooglePalette
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.panels.headerLabel
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.text.DateFormatUtil
import javax.swing.Icon
import javax.swing.JComponent

class BuildSummaryNode(
  val buildSummary: BuildSummary,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Build:") {

  private val chartItems: List<TimeDistributionChart.ChartDataItem<String>> = listOf(
    SimpleChartItem(buildSummary.configurationDuration, "Build configuration", categoricalGooglePalette[0]),
    SimpleChartItem(buildSummary.criticalPathDuration, "Tasks execution", categoricalGooglePalette[1]),
    MiscGradleStepsChartItem(buildSummary.miscStepsTime)
  )
  override val presentationIcon: Icon? = null
  override val issuesCountsSuffix = "finished at ${buildFinishedTime()}"
  override val timeSuffix = buildSummary.totalBuildDuration.durationString()
  override fun buildChildren() = emptyArray<SimpleNode>()
  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : ChartBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent {
      return headerLabel("Build finished at ${buildFinishedTime()}")
    }

    override fun createChart(): JComponent = TimeDistributionChart(chartItems, null, true)

    override fun createRightInfoPanel(): JComponent? = null
    override fun createLegend(): JComponent? = null
    override fun createDescription(): JComponent =
      JBLabel("Total build duration was ${buildSummary.totalBuildDuration.durationString()}.")
  }

  override val pageType = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY

  private fun buildFinishedTime(): String = DateFormatUtil.formatDateTime(buildSummary.buildFinishedTimestamp)

  class SimpleChartItem(
    private val time: TimeWithPercentage,
    private val text: String,
    private val color: CriticalPathChartLegend.ChartColor
  ) : TimeDistributionChart.ChartDataItem<String> {
    override fun time(): TimeWithPercentage = time
    override fun text(): String = text
    override fun getLegendColor(): CriticalPathChartLegend.ChartColor = color
    override fun getTableIcon(): Icon? = null
    override fun chartBoxText(): String? = null
  }
}