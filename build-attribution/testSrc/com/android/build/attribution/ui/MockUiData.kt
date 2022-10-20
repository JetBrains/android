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
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoriesUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskCategoryIssueUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.mockito.Mockito
import java.time.Duration
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
  val tasksList: List<TestTaskUiData> = emptyList(),
  val createTaskCategoryWarning: Boolean = false
) : BuildAttributionReportUiData {
  override val buildRequestData: GradleBuildInvoker.Request.RequestData
    get() = throw UnsupportedOperationException("Should be overridden for tests requiring to access the request.")
  override var buildSummary = mockBuildOverviewData()
  override var criticalPathTasks = mockCriticalPathTasksUiData()
  override var criticalPathPlugins = mockCriticalPathPluginsUiData()
  override val criticalPathTaskCategories = mockCriticalPathTaskCategoriesUiData()
  override var issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
  override var configurationTime = Mockito.mock(ConfigurationUiData::class.java)
  override var annotationProcessors = mockAnnotationProcessorsData()
  override var confCachingData: ConfigurationCachingCompatibilityProjectResult = NoIncompatiblePlugins(emptyList())
  override var jetifierData: JetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(JetifierUsedCheckRequired)
  override var downloadsData: DownloadsAnalyzer.Result = DownloadsAnalyzer.ActiveResult(repositoryResults = emptyList())
  override val showTaskCategoryInfo: Boolean
    get() = StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.get()

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

  fun mockCriticalPathTaskCategoriesUiData() = object : CriticalPathTaskCategoriesUiData {
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val entries = tasksList
      .groupBy{ it.primaryTaskCategory }
      .map{ createTaskCategoryData(it.key, it.value) }
    override val warningCount = entries.sumOf { it.warningCount }
    override val infoCount = entries.sumOf { it.infoCount }
  }

  fun mockCriticalPathPluginsUiData() = object : CriticalPathPluginsUiData {
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val entries: List<CriticalPathPluginUiData> = tasksList
      .groupBy { it.pluginName }
      .map { createPluginData(it.key, it.value) }
    override val warningCount: Int = entries.sumBy { it.warningCount }
    override val infoCount: Int = entries.sumBy { it.infoCount }
  }

  fun createTaskCategoryData(taskCategory: TaskCategory, tasks: List<TaskUiData>) = object : CriticalPathTaskCategoryUiData {
    override val name = taskCategory.displayName()
    override val taskCategory = taskCategory
    override val criticalPathTasks = tasks
    override val criticalPathDuration = TimeWithPercentage(tasks.sumByLong { it.executionTime.timeMs }, criticalPathDurationMs)
    override val issues = tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
    override val warningCount = tasks.count { it.hasWarning }
    override val infoCount = tasks.count { it.hasInfo }
    override val taskCategoryDescription: String
      get() = taskCategory.description

    override fun getTaskCategoryIssues(severity: TaskCategoryIssue.Severity, forWarningsPage: Boolean): List<TaskCategoryIssueUiData> {
      return if (createTaskCategoryWarning && severity == TaskCategoryIssue.Severity.WARNING) {
        listOfNotNull(
          TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED.takeIf { taskCategory == TaskCategory.ANDROID_RESOURCES },
          TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR.takeIf {
            !forWarningsPage && taskCategory == TaskCategory.JAVA
          }
        ).map {
          TaskCategoryIssueUiData(
            it,
            it.getWarningMessage(annotationProcessors.nonIncrementalProcessors.map {
              AnnotationProcessorData(it.className, Duration.ofMillis(it.compilationTimeMs))
            }),
            it.getLink()
          )
        }
      } else {
        emptyList()
      }
    }
  }

  fun createPluginData(name: String, tasks: List<TaskUiData>) = object : CriticalPathPluginUiData {
    override val name = name
    override val criticalPathTasks = tasks
    override val criticalPathDuration = TimeWithPercentage(tasks.sumByLong { it.executionTime.timeMs }, criticalPathDurationMs)
    override val issues = tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
    override val warningCount = tasks.count { it.hasWarning }
    override val infoCount = tasks.count { it.hasInfo }
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

fun mockDownloadsData(): DownloadsAnalyzer.ActiveResult {
  fun defaultDownloadResult(repository: DownloadsAnalyzer.Repository, status: DownloadsAnalyzer.DownloadStatus, duration: Long, bytes: Long): DownloadsAnalyzer.DownloadResult = DownloadsAnalyzer.DownloadResult(
    timestamp = 0,
    repository = repository,
    url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.pom",
    status = status,
    duration = duration,
    bytes = bytes,
    failureMessage = null
  )
  return DownloadsAnalyzer.ActiveResult(repositoryResults = listOf(
    DownloadsAnalyzer.RepositoryResult(
      repository = DownloadsAnalyzer.KnownRepository.GOOGLE,
      downloads = listOf(
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.GOOGLE, DownloadsAnalyzer.DownloadStatus.SUCCESS, 100, 60000),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.GOOGLE, DownloadsAnalyzer.DownloadStatus.SUCCESS, 100, 60000),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.GOOGLE, DownloadsAnalyzer.DownloadStatus.SUCCESS, 100, 60000),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.GOOGLE, DownloadsAnalyzer.DownloadStatus.SUCCESS, 400, 60000),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.GOOGLE, DownloadsAnalyzer.DownloadStatus.SUCCESS, 300, 60000)
      )
    ),
    DownloadsAnalyzer.RepositoryResult(
      repository = DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL,
      downloads = listOf(
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL, DownloadsAnalyzer.DownloadStatus.SUCCESS, 500, 10000),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL, DownloadsAnalyzer.DownloadStatus.MISSED, 10, 0),
        defaultDownloadResult(DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL, DownloadsAnalyzer.DownloadStatus.FAILURE, 5, 0)
      )
    )
  ))
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
  override var onExtendedCriticalPath: Boolean = true
  override var sourceType: PluginSourceType = PluginSourceType.ANDROID_PLUGIN
  override var reasonsToRun: List<String> = emptyList()
  override var issues: List<TaskIssueUiData> = emptyList()
  override val primaryTaskCategory: TaskCategory = TaskCategory.ANDROID_RESOURCES
  override val secondaryTaskCategories: List<TaskCategory> = listOf(TaskCategory.COMPILATION)
}
