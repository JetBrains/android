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
package com.android.build.attribution.ui.data

import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.build.attribution.ui.data.builder.AbstractBuildAttributionReportBuilderTest
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.ide.common.repository.GradleVersion
import com.google.common.truth.Truth
import com.intellij.util.text.DateFormatUtil
import org.junit.Test

class TaskIssueReportGeneratorTest : AbstractBuildAttributionReportBuilderTest() {

  val task1androidPlugin =
    TaskData("compileDebugJavaWithJavac", ":module1", applicationPlugin, 0, 400, TaskData.TaskExecutionMode.FULL, emptyList())
      .apply {
        setTaskType("org.gradle.api.tasks.compile.JavaCompile")
        isOnTheCriticalPath = true
      }

  val taskAmodule1 = TaskData("taskA", ":module1", pluginA, 0, 400, TaskData.TaskExecutionMode.FULL, emptyList())

  val taskBmodule1 = TaskData("taskB", ":module1", pluginB, 0, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    .apply { isOnTheCriticalPath = true }
  val taskBmodule2 = TaskData("taskB", ":module2", pluginB, 0, 100, TaskData.TaskExecutionMode.INCREMENTAL, emptyList())
  val taskBmodule3OtherPlugin = TaskData("taskB", ":module3", pluginC, 0, 300, TaskData.TaskExecutionMode.FULL, emptyList())

  val taskCmodule1 = TaskData("taskC", ":module1", pluginC, 0, 7300, TaskData.TaskExecutionMode.FULL, emptyList())
    .apply { isOnTheCriticalPath = true }

  private val mockAnalysisResult = object : AbstractBuildAttributionReportBuilderTest.MockResultsProvider() {
    override fun getTotalBuildTimeMs(): Long = 10000
    override fun getTasksDeterminingBuildDuration(): List<TaskData> = listOf(task1androidPlugin, taskBmodule1, taskCmodule1)
    override fun getProjectsConfigurationData(): List<ProjectConfigurationData> = listOf(
      project(":module1", 1000, listOf(
        plugin(pluginA, 200),
        plugin(pluginB, 100),
        plugin(pluginC, 700)
      ))
    )

    override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = listOf(
      AlwaysRunTaskData(task1androidPlugin, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS),

      AlwaysRunTaskData(taskAmodule1, AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE),
      AlwaysRunTaskData(taskBmodule1, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS),
      AlwaysRunTaskData(taskBmodule2, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS),
      AlwaysRunTaskData(taskBmodule3OtherPlugin, AlwaysRunTaskData.Reason.NO_OUTPUTS_WITHOUT_ACTIONS)
    )

    override fun getTasksSharingOutput(): List<TasksSharingOutputData> = listOf(
      TasksSharingOutputData("/tmp/tasks_sharing_output/test/path", listOf(taskAmodule1, taskBmodule1))
    )
  }

  // 2019-11-19 17:12:13
  private val buildFinishedTimestamp = 1574183533000
  private val buildFinishedTimeString = DateFormatUtil.formatDateTime(buildFinishedTimestamp)
  private val buildReportData = BuildAttributionReportBuilder(mockAnalysisResult, buildFinishedTimestamp).build()
  private val reporter = TaskIssueReportGenerator(
    buildReportData,
    { "AI-192.6817.14.36.SNAPSHOT, JRE 1.8.0_212-release-1586-b4-5784211x64 JetBrains s.r.o, OS Linux(amd64) v4.19.67-2rodete2-amd64, screens 2560x1440, 1440x2560" },
    { listOf(GradleVersion.parse("4.0.0-dev")) }
  )

  @Test
  fun testAlwaysRunSingleTaskReported() {
    val expectedTitle = "Always-run Tasks No Output Declared: com.android.application compileDebugJavaWithJavac"
    val expectedKey = "com.android.application_compileDebugJavaWithJavac_AlwaysRunNoOutputIssue"
    val expectedText = """
At 17:12, Nov 19, 2019, Android Studio detected an issue with Gradle plugin com.android.application

Always-run Tasks
Task runs on every build because it declares no outputs.

Plugin: com.android.application
Task: compileDebugJavaWithJavac
Task type: org.gradle.api.tasks.compile.JavaCompile
Issue detected in 1 module(s), total execution time was 0.400 s (5.0%), by module:
  Execution mode: FULL, time: 0.400 s (5.0%), determines build duration: true, on critical path: true

====Build information:====
Execution date: $buildFinishedTimeString
Total build duration: 10.000 s
Configuration time: 1.000 s (10.0%)
Critical path tasks time: 8.000 s (80.0%)
Critical path tasks size: 3
AGP versions: 4.0.0-dev
====Platform information:====
AI-192.6817.14.36.SNAPSHOT, JRE 1.8.0_212-release-1586-b4-5784211x64 JetBrains s.r.o, OS Linux(amd64) v4.19.67-2rodete2-amd64, screens 2560x1440, 1440x2560
""".trimIndent()

    val issue = buildReportData.findIssueFor(task1androidPlugin, TaskIssueType.ALWAYS_RUN_TASKS)
    Truth.assertThat(reporter.generateReportTitle(issue)).isEqualTo(expectedTitle)
    Truth.assertThat(reporter.generateIssueKey(issue)).isEqualTo(expectedKey)
    Truth.assertThat(reporter.generateReportText(issue)).isEqualTo(expectedText)
  }

  @Test
  fun testAlwaysRunUpToDateOverrideIssue() {
    val expectedTitle = "Always-run Tasks Up-To-Date Override: pluginA taskA"
    val expectedKey = "pluginA_taskA_AlwaysRunUpToDateOverride"
    val expectedText = """
At 17:12, Nov 19, 2019, Android Studio detected an issue with Gradle plugin pluginA

Always-run Tasks
This task might be setting its up-to-date check to always return false.

Plugin: pluginA
Task: taskA
Task type: UNKNOWN
Issue detected in 1 module(s), total execution time was 0.400 s (5.0%), by module:
  Execution mode: FULL, time: 0.400 s (5.0%), determines build duration: false, on critical path: false

====Build information:====
Execution date: $buildFinishedTimeString
Total build duration: 10.000 s
Configuration time: 1.000 s (10.0%)
Critical path tasks time: 8.000 s (80.0%)
Critical path tasks size: 3
AGP versions: 4.0.0-dev
====Platform information:====
AI-192.6817.14.36.SNAPSHOT, JRE 1.8.0_212-release-1586-b4-5784211x64 JetBrains s.r.o, OS Linux(amd64) v4.19.67-2rodete2-amd64, screens 2560x1440, 1440x2560
""".trim()


    val issue = buildReportData.findIssueFor(taskAmodule1, TaskIssueType.ALWAYS_RUN_TASKS)
    Truth.assertThat(reporter.generateReportTitle(issue)).isEqualTo(expectedTitle)
    Truth.assertThat(reporter.generateIssueKey(issue)).isEqualTo(expectedKey)
    Truth.assertThat(reporter.generateReportText(issue)).isEqualTo(expectedText)
  }

  @Test
  fun testTaskSetupIssue() {
    val expectedTitle = "Task Setup Issues: pluginA taskA"
    val expectedKey = "pluginA_taskA_TaskSetupIssue"
    val expectedText = """
At 17:12, Nov 19, 2019, Android Studio detected an issue with Gradle plugin pluginA

Task Setup Issues
Task declares the same output directory as task taskB from pluginB.

Plugin: pluginA
Task: taskA
Task type: UNKNOWN
Issue detected in 1 module(s), total execution time was 0.400 s (5.0%), by module:
  Execution mode: FULL, time: 0.400 s (5.0%), determines build duration: false, on critical path: false

====Build information:====
Execution date: $buildFinishedTimeString
Total build duration: 10.000 s
Configuration time: 1.000 s (10.0%)
Critical path tasks time: 8.000 s (80.0%)
Critical path tasks size: 3
AGP versions: 4.0.0-dev
====Platform information:====
AI-192.6817.14.36.SNAPSHOT, JRE 1.8.0_212-release-1586-b4-5784211x64 JetBrains s.r.o, OS Linux(amd64) v4.19.67-2rodete2-amd64, screens 2560x1440, 1440x2560
""".trim()


    val issue = buildReportData.findIssueFor(taskAmodule1, TaskIssueType.TASK_SETUP_ISSUE)
    Truth.assertThat(reporter.generateReportTitle(issue)).isEqualTo(expectedTitle)
    Truth.assertThat(reporter.generateIssueKey(issue)).isEqualTo(expectedKey)
    Truth.assertThat(reporter.generateReportText(issue)).isEqualTo(expectedText)
  }

  @Test
  fun testSeveralVersionsOfAgpInProjects() {
    val reporter = TaskIssueReportGenerator(
      buildReportData,
      { "" },
      { listOf(GradleVersion.parse("4.0.0-dev"), GradleVersion.parse("4.0.0-dev"), GradleVersion.parse("3.0.0-dev")) }
    )

    val issue = buildReportData.findIssueFor(task1androidPlugin, TaskIssueType.ALWAYS_RUN_TASKS)
    val agpVersionsLine = reporter.generateReportText(issue).lines().find { it.startsWith("AGP versions: ") }
    Truth.assertThat(agpVersionsLine).isEqualTo("AGP versions: 4.0.0-dev, 3.0.0-dev")
  }

  @Test
  fun testSameIssueInSeveralModules() {
    val expectedTitle = "Always-run Tasks No Output Declared: pluginB taskB"
    val expectedKey = "pluginB_taskB_AlwaysRunNoOutputIssue"
    val expectedText = """
At 17:12, Nov 19, 2019, Android Studio detected an issue with Gradle plugin pluginB

Always-run Tasks
Task runs on every build because it declares no outputs.

Plugin: pluginB
Task: taskB
Task type: UNKNOWN
Issue detected in 2 module(s), total execution time was 0.400 s (5.0%), by module:
  Execution mode: FULL, time: 0.300 s (3.8%), determines build duration: true, on critical path: true
  Execution mode: INCREMENTAL, time: 0.100 s (1.3%), determines build duration: false, on critical path: false

====Build information:====
Execution date: $buildFinishedTimeString
Total build duration: 10.000 s
Configuration time: 1.000 s (10.0%)
Critical path tasks time: 8.000 s (80.0%)
Critical path tasks size: 3
AGP versions: 4.0.0-dev
====Platform information:====
AI-192.6817.14.36.SNAPSHOT, JRE 1.8.0_212-release-1586-b4-5784211x64 JetBrains s.r.o, OS Linux(amd64) v4.19.67-2rodete2-amd64, screens 2560x1440, 1440x2560
""".trim()

    val issue = buildReportData.findIssueFor(taskBmodule1, TaskIssueType.ALWAYS_RUN_TASKS)
    Truth.assertThat(reporter.generateReportTitle(issue)).isEqualTo(expectedTitle)
    Truth.assertThat(reporter.generateIssueKey(issue)).isEqualTo(expectedKey)
    Truth.assertThat(reporter.generateReportText(issue)).isEqualTo(expectedText)
  }
}

fun BuildAttributionReportUiData.findIssueFor(task: TaskData, type: TaskIssueType): TaskIssueUiData {
  return issues
    .find { it.type == type }!!
    .issues.find { it.task.taskPath == task.getTaskPath() && it.task.pluginName == task.originPlugin.displayName }!!
}
