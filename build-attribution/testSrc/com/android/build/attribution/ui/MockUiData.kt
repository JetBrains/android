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
package com.android.build.attribution.ui

import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginTasksUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.mockito.Mockito
import java.util.Calendar

const val defaultTotalBuildDurationMs: Long = 20000L
const val defaultCriticalPathDurationMs: Long = 15000L
const val defaultConfigurationDurationMs: Long = 4000L

fun mockTask(
  module: String,
  name: String,
  pluginName: String,
  executionTimeMs: Long,
  criticalPathDurationMs: Long = defaultCriticalPathDurationMs,
  pluginUnknownBecauseOfCC: Boolean = false
) = TestTaskUiData(module, name, pluginName, TimeWithPercentage(executionTimeMs, criticalPathDurationMs), pluginUnknownBecauseOfCC)

class MockUiData(
  val totalBuildDurationMs: Long = defaultTotalBuildDurationMs,
  val criticalPathDurationMs: Long = defaultCriticalPathDurationMs,
  val configurationDurationMs: Long = defaultConfigurationDurationMs,
  val gcTimeMs: Long = 0,
  val tasksList: List<TestTaskUiData> = emptyList()
) : BuildAttributionReportUiData {
  override val successfulBuild = true
  override var buildSummary = mockBuildOverviewData()
  override var criticalPathTasks = mockCriticalPathTasksUiData()
  override var criticalPathPlugins = mockCriticalPathPluginsUiData()
  override var issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
  override var configurationTime = Mockito.mock(ConfigurationUiData::class.java)
  override var annotationProcessors = mockAnnotationProcessorsData()
  override var confCachingData: ConfigurationCachingCompatibilityProjectResult = NoIncompatiblePlugins(emptyList())

  fun mockBuildOverviewData(
    javaVersionUsed: Int? = null,
    isGarbageCollectorSettingSet: Boolean? = null
  ) = object : BuildSummary {
    override val buildFinishedTimestamp = Calendar.getInstance().let {
      it.set(2020, 0, 30, 12, 21)
      it.timeInMillis
    }
    override val totalBuildDuration = TimeWithPercentage(totalBuildDurationMs, totalBuildDurationMs)
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val configurationDuration = TimeWithPercentage(configurationDurationMs, totalBuildDurationMs)
    override val garbageCollectionTime = TimeWithPercentage(gcTimeMs, totalBuildDurationMs)
    override val javaVersionUsed: Int? = javaVersionUsed
    override val isGarbageCollectorSettingSet: Boolean? = isGarbageCollectorSettingSet
  }

  fun mockCriticalPathTasksUiData() = object : CriticalPathTasksUiData {
    override val tasks: List<TaskUiData> = tasksList
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val warningCount: Int = tasks.count { it.hasWarning }
    override val infoCount: Int = tasks.count { it.hasInfo }
  }

  fun mockTask(module: String, name: String, pluginName: String, executionTimeMs: Long) =
    mockTask(module, name, pluginName, executionTimeMs, criticalPathDurationMs)

  fun mockCriticalPathPluginsUiData() = object : CriticalPathPluginsUiData {
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val plugins: List<CriticalPathPluginUiData> = tasksList
      .groupBy { it.pluginName }
      .map { createPluginData(it.key, it.value) }
    override val warningCount: Int = plugins.sumBy { it.warningCount }
    override val infoCount: Int = plugins.sumBy { it.infoCount }
  }

  fun createPluginData(name: String, tasks: List<TaskUiData>) = object : CriticalPathPluginUiData {
    override val name = name
    override val criticalPathTasks = object : CriticalPathPluginTasksUiData {
      override val tasks = tasks
      override val criticalPathDuration = TimeWithPercentage(tasks.sumByLong { it.executionTime.timeMs }, criticalPathDurationMs)
      override val warningCount = tasks.count { it.hasWarning }
      override val infoCount = tasks.count { it.hasInfo }
    }
    override val criticalPathDuration = criticalPathTasks.criticalPathDuration
    override val issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
    override val warningCount = criticalPathTasks.warningCount
    override val infoCount = criticalPathTasks.infoCount
  }

  fun createIssuesGroup(type: TaskIssueType, issues: List<TaskIssueUiData>) = object : TaskIssuesGroup {
    override val type = type
    override val issues: List<TaskIssueUiData> = issues.sortedByDescending { it.task.executionTime }
    override val timeContribution =
      TimeWithPercentage(issues.map { it.task.executionTime.timeMs }.sum(), totalBuildDurationMs)
  }

  fun mockAnnotationProcessorsData() = object : AnnotationProcessorsReport {
    override val nonIncrementalProcessors = listOf(
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoAnnotationProcessor"
        override val compilationTimeMs = 123L
      },
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoValueBuilderProcessor"
        override val compilationTimeMs = 456L
      },
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoOneOfProcessor"
        override val compilationTimeMs = 789L
      }
    )
  }
}

class TestTaskUiData(
  override val module: String,
  override val name: String,
  override var pluginName: String,
  override val executionTime: TimeWithPercentage,
  override val pluginUnknownBecauseOfCC: Boolean = false
) : TaskUiData {
  override val taskPath: String = "$module:$name"
  override val taskType: String = "CompilationType"
  override val executedIncrementally: Boolean = true
  override val executionMode: String = "FULL"
  override var onLogicalCriticalPath: Boolean = true
  override val onExtendedCriticalPath: Boolean = true
  override var sourceType: PluginSourceType = PluginSourceType.ANDROID_PLUGIN
  override val reasonsToRun: List<String> = emptyList()
  override var issues: List<TaskIssueUiData> = emptyList()
}
