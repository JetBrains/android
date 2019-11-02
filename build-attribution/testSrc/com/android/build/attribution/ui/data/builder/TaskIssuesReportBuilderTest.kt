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

import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.build.attribution.ui.data.CriticalPathPluginTasksUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueType.ALWAYS_RUN_TASKS
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskIssuesReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testIssuesOnTasksCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 400, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskB = TaskData("taskB", ":app", pluginB, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskC = TaskData("taskC", ":lib", pluginA, 200, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskD = TaskData("taskD", ":app", pluginB, 100, TaskData.TaskExecutionMode.FULL, emptyList())

    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTime(): Long = 1500
      override fun getCriticalPathDuration(): Long = 1000
      override fun getTasksCriticalPath(): List<TaskData> = listOf(taskA, taskB, taskC, taskD)
      override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = listOf(
        CriticalPathAnalyzer.PluginBuildData(pluginA, 400),
        CriticalPathAnalyzer.PluginBuildData(pluginB, 600)
      )

      override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = listOf(
        AlwaysRunTaskData(taskA, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS),
        AlwaysRunTaskData(taskB, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS),
        AlwaysRunTaskData(taskC, AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
      )

      override fun getTasksSharingOutput(): List<TasksSharingOutputData> = listOf(
        TasksSharingOutputData("/tmp/tasks_sharing_output/test/path", listOf(taskA, taskC))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    assertThat(report.criticalPathTasks.size).isEqualTo(4)
    assertThat(report.criticalPathTasks.warningCount).isEqualTo(3)
    assertThat(report.criticalPathTasks.infoCount).isEqualTo(0)
    // Check issues assigned for each task.
    report.criticalPathTasks.tasks[0].verifyIssues(":app:taskA", listOf(ALWAYS_RUN_TASKS), warningExpected = true, infoExpected = false)
    report.criticalPathTasks.tasks[1].verifyIssues(":app:taskB", listOf(ALWAYS_RUN_TASKS), warningExpected = true, infoExpected = false)
    report.criticalPathTasks.tasks[2].verifyIssues(":lib:taskC", listOf(ALWAYS_RUN_TASKS), warningExpected = true, infoExpected = false)
    report.criticalPathTasks.tasks[3].verifyIssues(":app:taskD", listOf(), warningExpected = false, infoExpected = false)
  }

  @Test
  fun testIssuesOnPluginsCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 400, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskB = TaskData("taskB", ":app", pluginB, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    val nonCritPathTask = TaskData("taskOther", ":app", pluginA, 100, TaskData.TaskExecutionMode.FULL, emptyList())

    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTime(): Long = 1500
      override fun getCriticalPathDuration(): Long = 1000
      override fun getTasksCriticalPath(): List<TaskData> = listOf(taskA, taskB)
      override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = listOf(
        CriticalPathAnalyzer.PluginBuildData(pluginA, 400),
        CriticalPathAnalyzer.PluginBuildData(pluginB, 300)
      )

      override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = listOf(
        AlwaysRunTaskData(taskA, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS),
        AlwaysRunTaskData(taskB, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS),
        AlwaysRunTaskData(nonCritPathTask, AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
      )

      override fun getNoncacheableTasks(): List<TaskData> = listOf(taskA)

      override fun getTasksSharingOutput(): List<TasksSharingOutputData> = listOf(
        TasksSharingOutputData("/tmp/tasks_sharing_output/test/path", listOf(taskA, nonCritPathTask))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    report.criticalPathPlugins.verify(expectedSize = 2, expectedWarnings = 3, expectedInfos = 0)
    // Only one taskA is on critical path but there is another issue for pluginA for the task not on critical path.
    report.criticalPathPlugins.plugins[0].verify(expectedTasksSize = 1, expectedWarnings = 2, expectedInfos = 0)
    report.criticalPathPlugins.plugins[0].criticalPathTasks.verify(expectedSize = 1, expectedWarnings = 1, expectedInfos = 0)
    assertThat(report.criticalPathPlugins.plugins[0].issues.size).isEqualTo(1)
    report.criticalPathPlugins.plugins[0].issues[0].verify(expectedType = ALWAYS_RUN_TASKS, expectedSize = 2, expectedWarnings = 2,
                                                           expectedInfos = 0)

    report.criticalPathPlugins.plugins[1].verify(expectedTasksSize = 1, expectedWarnings = 1, expectedInfos = 0)
    report.criticalPathPlugins.plugins[1].criticalPathTasks.verify(expectedSize = 1, expectedWarnings = 1, expectedInfos = 0)
    assertThat(report.criticalPathPlugins.plugins[1].issues.map { it.type }).isEqualTo(listOf(ALWAYS_RUN_TASKS))
  }

  @Test
  fun testIssuesNotOnCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 400, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskB = TaskData("taskB", ":app", pluginB, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    val nonCritPathTask = TaskData("taskOther", ":app", pluginA, 100, TaskData.TaskExecutionMode.FULL, emptyList())

    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTime(): Long = 1500
      override fun getCriticalPathDuration(): Long = 1000
      override fun getTasksCriticalPath(): List<TaskData> = listOf(taskA, taskB)
      override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = listOf(
        CriticalPathAnalyzer.PluginBuildData(pluginA, 400),
        CriticalPathAnalyzer.PluginBuildData(pluginB, 600)
      )

      override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = listOf(
        AlwaysRunTaskData(taskA, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS),
        AlwaysRunTaskData(taskB, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS),
        AlwaysRunTaskData(nonCritPathTask, AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE)
      )

      override fun getNoncacheableTasks(): List<TaskData> = listOf(taskA)

      override fun getTasksSharingOutput(): List<TasksSharingOutputData> = listOf(
        TasksSharingOutputData("/tmp/tasks_sharing_output/test/path", listOf(taskA, nonCritPathTask))
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    assertThat(report.issues.size).isEqualTo(1)
    report.issues[0].verify(ALWAYS_RUN_TASKS, 3, 3, 0)
  }

  private fun CriticalPathPluginTasksUiData.verify(
    expectedSize: Int,
    expectedWarnings: Int,
    expectedInfos: Int
  ) {
    assertThat(size).isEqualTo(expectedSize)
    assertThat(tasks.size).isEqualTo(expectedSize)
    assertThat(warningCount).isEqualTo(expectedWarnings)
    assertThat(infoCount).isEqualTo(expectedInfos)
  }

  private fun CriticalPathPluginsUiData.verify(
    expectedSize: Int,
    expectedWarnings: Int,
    expectedInfos: Int
  ) {
    assertThat(plugins.size).isEqualTo(expectedSize)
    assertThat(warningCount).isEqualTo(expectedWarnings)
    assertThat(infoCount).isEqualTo(expectedInfos)
  }

  private fun CriticalPathPluginUiData.verify(
    expectedTasksSize: Int,
    expectedWarnings: Int,
    expectedInfos: Int
  ) {
    assertThat(criticalPathTasks.size).isEqualTo(expectedTasksSize)
    assertThat(warningCount).isEqualTo(expectedWarnings)
    assertThat(infoCount).isEqualTo(expectedInfos)
  }

  private fun TaskIssuesGroup.verify(
    expectedType: TaskIssueType,
    expectedSize: Int,
    expectedWarnings: Int,
    expectedInfos: Int
  ) {
    assertThat(type).isEqualTo(expectedType)
    assertThat(size).isEqualTo(expectedSize)
    assertThat(issues.size).isEqualTo(expectedSize)
    assertThat(warningCount).isEqualTo(expectedWarnings)
    assertThat(infoCount).isEqualTo(expectedInfos)
  }

  private fun TaskUiData.verifyIssues(
    pathExpected: String,
    issuesExpected: List<TaskIssueType>,
    warningExpected: Boolean,
    infoExpected: Boolean
  ) {
    assertThat(taskPath).isEqualTo(pathExpected)
    assertThat(issues.map { it.type }).isEqualTo(issuesExpected)
    assertThat(hasWarning).isEqualTo(warningExpected)
    assertThat(hasInfo).isEqualTo(infoExpected)
    // Verify all back-references from issue to task point to the same task object.
    issues.forEach { assertThat(it.task).isSameAs(this) }
  }
}