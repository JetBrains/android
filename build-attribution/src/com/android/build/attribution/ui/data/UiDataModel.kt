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

import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.ide.common.attribution.TaskCategory
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker

/*
 * The set of interfaces in this file represents the build attribution report data model and is used for any data access from the UI.
 * These interfaces correspond to the tree structure of UI info panels and contain all data required for presentation.
 *
 * All UI report implementations should get data from these interfaces and should never depend on any of the backend classes directly.
 * This model should provide data in a state that can be directly presented on the UI without further processing
 * (e.g. should be properly sorted).
 */

interface BuildAttributionReportUiData {
  val successfulBuild: Boolean
  val buildRequestData: GradleBuildInvoker.Request.RequestData
  val buildSummary: BuildSummary
  val criticalPathTasks: CriticalPathTasksUiData
  val criticalPathPlugins: CriticalPathPluginsUiData
  val criticalPathTaskCategories: CriticalPathTaskCategoriesUiData
  /**
   * All detected issues grouped by issue type
   */
  val issues: List<TaskIssuesGroup>
  val configurationTime: ConfigurationUiData
  val annotationProcessors: AnnotationProcessorsReport
  val confCachingData: ConfigurationCachingCompatibilityProjectResult
  val jetifierData: JetifierUsageAnalyzerResult
  val downloadsData: DownloadsAnalyzer.Result
}

interface BuildSummary {
  val buildFinishedTimestamp: Long
  val totalBuildDuration: TimeWithPercentage
  val criticalPathDuration: TimeWithPercentage
  val configurationDuration: TimeWithPercentage
  val miscStepsTime: TimeWithPercentage
    get() = TimeWithPercentage(
      totalBuildDuration.timeMs - configurationDuration.timeMs - criticalPathDuration.timeMs,
      totalBuildDuration.totalMs
    )
  val garbageCollectionTime: TimeWithPercentage
  val javaVersionUsed: Int?
  val isGarbageCollectorSettingSet: Boolean?
}

interface CriticalPathTasksUiData {
  val criticalPathDuration: TimeWithPercentage
  val miscStepsTime: TimeWithPercentage
  val tasks: List<TaskUiData>
  val size: Int
    get() = tasks.size
  val warningCount: Int
  val infoCount: Int
}

interface CriticalPathPluginsUiData: CriticalPathEntriesUiData {
  override val entries: List<CriticalPathPluginUiData>
}

interface CriticalPathTaskCategoriesUiData: CriticalPathEntriesUiData{
  override val entries: List<CriticalPathTaskCategoryUiData>
}

// Model UI object that represents a list of plugins/ task labels
interface CriticalPathEntriesUiData {
  val criticalPathDuration: TimeWithPercentage
  val miscStepsTime: TimeWithPercentage
  val entries: List<CriticalPathEntryUiData>
  val warningCount: Int
  val infoCount: Int
}

interface TaskUiData {
  val module: String
  val name: String
  val taskPath: String
  val taskType: String
  val executionTime: TimeWithPercentage
  val executedIncrementally: Boolean
  val executionMode: String
  /** True for tasks that belong to a critical path based on task dependencies analysis.*/
  val onLogicalCriticalPath: Boolean
  /** True for tasks that belong effective critical path based on execution times analysis.*/
  val onExtendedCriticalPath: Boolean
  val pluginName: String
  val sourceType: PluginSourceType
  val pluginUnknownBecauseOfCC: Boolean
    get() = false
  val reasonsToRun: List<String>
  val issues: List<TaskIssueUiData>
  val hasWarning: Boolean
    get() = issues.any { it.type.level == IssueLevel.WARNING }
  val hasInfo: Boolean
    get() = issues.any { it.type.level == IssueLevel.INFO }
  val primaryTaskCategory: TaskCategory
  val secondaryTaskCategories: List<TaskCategory>
}

enum class PluginSourceType {
  ANDROID_PLUGIN, BUILD_SRC, THIRD_PARTY
}

interface CriticalPathPluginUiData : CriticalPathEntryUiData {
  override val modelGrouping: TasksDataPageModel.Grouping
    get() = TasksDataPageModel.Grouping.BY_PLUGIN
}

interface CriticalPathTaskCategoryUiData : CriticalPathEntryUiData {
  val taskCategoryInfo: String
  override val modelGrouping: TasksDataPageModel.Grouping
    get() = TasksDataPageModel.Grouping.BY_TASK_CATEGORY
}

// Model UI object that represents a plugin / task label
interface CriticalPathEntryUiData {
  val name: String
  /** Total time of this plugin tasks on critical path. */
  val criticalPathDuration: TimeWithPercentage
  /** This plugin tasks on critical path. */
  val criticalPathTasks: List<TaskUiData>
  val size: Int
    get() = criticalPathTasks.size
  val issues: List<TaskIssuesGroup>
  val warningCount: Int
  val infoCount: Int
  val modelGrouping: TasksDataPageModel.Grouping
}

/**
 * Represents issues list of one type.
 */
interface TaskIssuesGroup {
  val type: TaskIssueType
  val issues: List<TaskIssueUiData>
  val size: Int
    get() = issues.size
  val warningCount: Int
    get() = if (type.level == IssueLevel.WARNING) size else 0
  val infoCount: Int
    get() = if (type.level == IssueLevel.INFO) size else 0
  val timeContribution: TimeWithPercentage
}

enum class IssueLevel { WARNING, INFO }

enum class TaskIssueType(
  val uiName: String,
  val level: IssueLevel
) {
  // Order is important and reflects sorting order on the UI.
  ALWAYS_RUN_TASKS("Always-Run Tasks", IssueLevel.WARNING),
  TASK_SETUP_ISSUE("Task Setup Issues", IssueLevel.WARNING),

}

interface TaskIssueUiData {
  val type: TaskIssueType
  val task: TaskUiData
  val bugReportTitle: String
  val bugReportBriefDescription: String
  val explanation: String
  val helpLink: BuildAnalyzerBrowserLinks
  val buildSrcRecommendation: String
}

/**
 * Represents an issue that has another task connected to it
 * e.g. For tasks declaring same output we want to show the original task and another task that declares same output.
 */
interface InterTaskIssueUiData : TaskIssueUiData {
  val connectedTask: TaskUiData
}

interface ConfigurationUiData {
  val totalConfigurationTime: TimeWithPercentage
  val projects: List<ProjectConfigurationUiData>
  val totalIssueCount: Int
}

interface ProjectConfigurationUiData {
  val project: String
  val configurationTime: TimeWithPercentage
  val plugins: List<PluginConfigurationUiData>
  val issueCount: Int
}

interface PluginConfigurationUiData {
  val pluginName: String
  val configurationTime: TimeWithPercentage
  val slowsConfiguration: Boolean
  val nestedPlugins: List<PluginConfigurationUiData>
  val nestedIssueCount: Int
}

interface AnnotationProcessorsReport {
  val nonIncrementalProcessors: List<AnnotationProcessorUiData>
  val issueCount: Int
    get() = nonIncrementalProcessors.size
}

interface AnnotationProcessorUiData {
  val className: String
  val compilationTimeMs: Long
}
