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

import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.warningIcon
import com.intellij.icons.AllIcons
import com.intellij.util.ui.EmptyIcon
import java.util.ArrayList
import javax.swing.Icon


const val MAX_ITEMS_SHOWN_SEPARATELY = 15

fun createTaskChartItems(data: CriticalPathTasksUiData): List<TimeDistributionChart.ChartDataItem<TaskUiData>> {
  val result = ArrayList<TimeDistributionChart.ChartDataItem<TaskUiData>>()
  val aggregatedTasks = ArrayList<TaskUiData>()
  data.tasks
    .sortedByDescending { it.executionTime.timeMs }
    .forEach { taskData ->
      if (result.size < MAX_ITEMS_SHOWN_SEPARATELY) {
        result.add(TaskChartItem(
          taskData = taskData,
          assignedColor = CriticalPathChartLegend.resolveTaskColor(taskData)
        ))
      }
      else {
        aggregatedTasks.add(taskData)
      }
    }

  when {
    aggregatedTasks.size > 1 -> result.add(OtherChartItem(
      time = TimeWithPercentage(aggregatedTasks.map { it.executionTime.timeMs }.sum(), data.criticalPathDuration.totalMs),
      textPrefix = "Short tasks",
      aggregatedItems = aggregatedTasks,
      assignedColor = CriticalPathChartLegend.OTHER_TASKS_COLOR,
      hasWarnings = aggregatedTasks.any { it.hasWarning }
    ))
    aggregatedTasks.size == 1 -> result.add(TaskChartItem(
      taskData = aggregatedTasks[0],
      assignedColor = CriticalPathChartLegend.resolveTaskColor(aggregatedTasks[0])
    ))
  }

  return result
}

fun createPluginChartItems(data: CriticalPathPluginsUiData): List<TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>> {
  val result = ArrayList<TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>>()
  val palette = CriticalPathChartLegend.pluginColorPalette
  palette.reset()
  val aggregatedPlugins = ArrayList<CriticalPathPluginUiData>()
  data.plugins
    .sortedByDescending { it.criticalPathDuration.timeMs }
    .forEach { pluginData ->
      if (result.size < MAX_ITEMS_SHOWN_SEPARATELY) {
        result.add(PluginChartItem(
          pluginData = pluginData,
          assignedColor = palette.getColor(pluginData.name)
        ))
      }
      else {
        aggregatedPlugins.add(pluginData)
      }
    }

  when {
    aggregatedPlugins.size > 1 -> result.add(OtherChartItem(
      time = TimeWithPercentage(aggregatedPlugins.map { it.criticalPathDuration.timeMs }.sum(), data.criticalPathDuration.totalMs),
      textPrefix = "Short plugins",
      aggregatedItems = aggregatedPlugins,
      assignedColor = palette.getOneColorForAll(aggregatedPlugins),
      hasWarnings = aggregatedPlugins.any { it.warningCount > 0 }
    ))
    aggregatedPlugins.size == 1 -> result.add(PluginChartItem(
      pluginData = aggregatedPlugins[0],
      assignedColor = palette.getColor(aggregatedPlugins[0].name)
    ))
  }

  return result
}


private class TaskChartItem(
  private val taskData: TaskUiData,
  private val assignedColor: CriticalPathChartLegend.ChartColor
) : TimeDistributionChart.SingularChartDataItem<TaskUiData> {

  override fun time(): TimeWithPercentage {
    return taskData.executionTime
  }

  override fun text(): String {
    return taskData.taskPath
  }

  override fun getTableIcon() = when {
    taskData.hasWarning -> warningIcon()
    taskData.hasInfo -> AllIcons.General.BalloonInformation
    else -> EmptyIcon.ICON_16
  }

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String? {
    return null
  }

  override fun getUnderlyingData(): TaskUiData {
    return taskData
  }
}

private class PluginChartItem(
  private val pluginData: CriticalPathPluginUiData,
  private val assignedColor: CriticalPathChartLegend.ChartColor
) : TimeDistributionChart.SingularChartDataItem<CriticalPathPluginUiData> {

  override fun time(): TimeWithPercentage {
    return pluginData.criticalPathDuration
  }

  override fun text(): String {
    return pluginData.name
  }

  override fun getTableIcon(): Icon? = when {
    pluginData.warningCount > 0 -> warningIcon()
    pluginData.infoCount > 0 -> AllIcons.General.BalloonInformation
    else -> EmptyIcon.ICON_16
  }

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String? {
    return null
  }

  override fun getUnderlyingData(): CriticalPathPluginUiData {
    return pluginData
  }
}

private class OtherChartItem<T>(
  private val time: TimeWithPercentage,
  private val textPrefix: String,
  private val aggregatedItems: List<T>,
  private val assignedColor: CriticalPathChartLegend.ChartColor,
  private val hasWarnings: Boolean
) : TimeDistributionChart.AggregatedChartDataItem<T> {

  override fun time(): TimeWithPercentage {
    return time
  }

  override fun text(): String {
    return textPrefix + String.format(" (%d)", aggregatedItems.size)
  }

  override fun getTableIcon(): Icon? = if (hasWarnings) warningIcon() else null

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String {
    return textPrefix
  }

  override fun getUnderlyingData(): List<T> {
    return aggregatedItems
  }
}
