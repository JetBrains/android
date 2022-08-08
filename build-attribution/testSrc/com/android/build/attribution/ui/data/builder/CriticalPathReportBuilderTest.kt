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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CriticalPathReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testTasksCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 0, 100, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskB = TaskData("taskB", ":app", pluginB, 0, 400, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskC = TaskData("taskC", ":lib", pluginA, 0, 300, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskD = TaskData("taskD", ":app", pluginB, 0, 200, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }


    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTimeMs(): Long = 1500
      override fun getTasksDeterminingBuildDuration(): List<TaskData> = listOf(taskA, taskB, taskC, taskD)
      override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> = listOf(
        PluginBuildData(pluginA, 400),
        PluginBuildData(pluginB, 600)
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345, mock()).build()

    assertThat(report.buildSummary.totalBuildDuration.timeMs).isEqualTo(1500)
    assertThat(report.criticalPathTasks.criticalPathDuration).isEqualTo(TimeWithPercentage(1000, 1500))
    assertThat(report.criticalPathTasks.miscStepsTime).isEqualTo(TimeWithPercentage(500, 1500))
    assertThat(report.criticalPathTasks.size).isEqualTo(4)
    //Sorted by time descending
    report.criticalPathTasks.tasks[0].verifyValues(":app", "taskB", pluginB, TimeWithPercentage(400, 1000))
    report.criticalPathTasks.tasks[1].verifyValues(":lib", "taskC", pluginA, TimeWithPercentage(300, 1000))
    report.criticalPathTasks.tasks[2].verifyValues(":app", "taskD", pluginB, TimeWithPercentage(200, 1000))
    report.criticalPathTasks.tasks[3].verifyValues(":app", "taskA", pluginA, TimeWithPercentage(100, 1000))
  }

  @Test
  fun testPluginsCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 0, 100, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskB = TaskData("taskB", ":app", pluginB, 0, 400, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskC = TaskData("taskC", ":lib", pluginA, 0, 300, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }
    val taskD = TaskData("taskD", ":app", pluginB, 0, 200, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }


    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTimeMs(): Long = 1500
      override fun getTasksDeterminingBuildDuration(): List<TaskData> = listOf(taskA, taskB, taskC, taskD)
      override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> = listOf(
        PluginBuildData(pluginA, 400),
        PluginBuildData(pluginB, 600)
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345, mock()).build()

    assertThat(report.criticalPathPlugins.criticalPathDuration).isEqualTo(TimeWithPercentage(1000, 1500))
    assertThat(report.criticalPathPlugins.miscStepsTime).isEqualTo(TimeWithPercentage(500, 1500))
    assertThat(report.criticalPathPlugins.entries.size).isEqualTo(2)
    assertThat(report.criticalPathPlugins.entries[0].name).isEqualTo("pluginB")
    assertThat(report.criticalPathPlugins.entries[0].criticalPathTasks.size).isEqualTo(2)
    report.criticalPathPlugins.entries[0].criticalPathTasks[0].verifyValues(":app", "taskB", pluginB, TimeWithPercentage(400, 1000))
    report.criticalPathPlugins.entries[0].criticalPathTasks[1].verifyValues(":app", "taskD", pluginB, TimeWithPercentage(200, 1000))
    assertThat(report.criticalPathPlugins.entries[0].criticalPathDuration).isEqualTo(TimeWithPercentage(600, 1000))
    assertThat(report.criticalPathPlugins.entries[1].name).isEqualTo("pluginA")
    assertThat(report.criticalPathPlugins.entries[1].criticalPathTasks.size).isEqualTo(2)
    report.criticalPathPlugins.entries[1].criticalPathTasks[0].verifyValues(":lib", "taskC", pluginA, TimeWithPercentage(300, 1000))
    report.criticalPathPlugins.entries[1].criticalPathTasks[1].verifyValues(":app", "taskA", pluginA, TimeWithPercentage(100, 1000))
    assertThat(report.criticalPathPlugins.entries[1].criticalPathDuration).isEqualTo(TimeWithPercentage(400, 1000))
  }

  @Test
  fun testTaskWithNoPluginInfoBecauseOfConfiguratioCache() {
    val plugin = PluginData(PluginData.PluginType.UNKNOWN, "")
    val taskA = TaskData("taskA", ":app", plugin, 0, 100, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply { isOnTheCriticalPath = true }


    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTimeMs(): Long = 1500
      override fun getTasksDeterminingBuildDuration(): List<TaskData> = listOf(taskA)
      override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> = listOf(PluginBuildData(plugin, 1500))
      override fun buildUsesConfigurationCache(): Boolean = true
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345, mock()).build()

    //Sorted by time descending
    assertThat(report.criticalPathTasks.tasks[0].pluginUnknownBecauseOfCC).isTrue()
  }

  private fun TaskUiData.verifyValues(project: String, name: String, plugin: PluginData, time: TimeWithPercentage) {
    assertThat(taskPath).isEqualTo("${project}:${name}")
    assertThat(module).isEqualTo(project)
    assertThat(pluginName).isEqualTo(plugin.displayNameInProject(project))
    assertThat(onLogicalCriticalPath).isTrue()
    assertThat(executionTime).isEqualTo(time)
  }
}