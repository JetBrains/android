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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import org.jetbrains.kotlin.idea.util.ifTrue

interface BuildEventsAnalysisResult {
  fun getBuildRequestData() : GradleBuildInvoker.Request.RequestData
  fun getBuildFinishedTimestamp() : Long
  fun getAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData>
  fun getTotalBuildTimeMs(): Long
  fun getConfigurationPhaseTimeMs(): Long
  fun getCriticalPathTasks(): List<TaskData>
  fun getTasksDeterminingBuildDuration(): List<TaskData>
  fun getPluginsDeterminingBuildDuration(): List<PluginBuildData>

  /**
   * Total configuration data summed over all subprojects.
   */
  fun getTotalConfigurationData(): ProjectConfigurationData

  /**
   * List of subprojects individual configuration data.
   */
  fun getProjectsConfigurationData(): List<ProjectConfigurationData>
  fun getAlwaysRunTasks(): List<AlwaysRunTaskData>
  fun getNonCacheableTasks(): List<TaskData>
  fun getTasksSharingOutput(): List<TasksSharingOutputData>

  /**
   * returns a list of all applied plugins for each configured project.
   * May contain internal plugins
   */
  fun getAppliedPlugins(): Map<String, List<PluginData>>

  /**
   * Result of configuration cache compatibility analysis, describes the state and incompatible plugins if any.
   */
  fun getConfigurationCachingCompatibility(): ConfigurationCachingCompatibilityProjectResult

  /**
   * Result Jetifier usage analyzer, describes the state of jetifier flags and AndroidX incompatible libraries if any.
   */
  fun getJetifierUsageResult(): JetifierUsageAnalyzerResult

  /**
   * List of garbage collection data for this build.
   */
  fun getGarbageCollectionData(): List<GarbageCollectionData>
  /**
   * Total time spent in garbage collection for this build.
   */
  fun getTotalGarbageCollectionTimeMs(): Long
  fun getJavaVersion(): Int?
  fun isGCSettingSet(): Boolean?
  fun buildUsesConfigurationCache(): Boolean
  fun getDownloadsAnalyzerResult(): DownloadsAnalyzer.Result

  fun getTaskCategoryWarningsAnalyzerResult(): TaskCategoryWarningsAnalyzer.Result
}

/**
 * A way of interaction between the build events analyzers and the build attribution manager.
 * Used to fetch the final data from the analyzers after the build is complete.
 */
class BuildEventsAnalyzersProxy(
  val taskContainer: TaskContainer,
  val pluginContainer: PluginContainer
) {
  val alwaysRunTasksAnalyzer = AlwaysRunTasksAnalyzer(taskContainer, pluginContainer)
  val annotationProcessorsAnalyzer = AnnotationProcessorsAnalyzer(taskContainer, pluginContainer)
  val criticalPathAnalyzer = CriticalPathAnalyzer(taskContainer, pluginContainer)
  val noncacheableTasksAnalyzer = NoncacheableTasksAnalyzer(taskContainer)
  val garbageCollectionAnalyzer = GarbageCollectionAnalyzer()
  val projectConfigurationAnalyzer = ProjectConfigurationAnalyzer(pluginContainer)
  val tasksConfigurationIssuesAnalyzer = TasksConfigurationIssuesAnalyzer(taskContainer)
  val configurationCachingCompatibilityAnalyzer = ConfigurationCachingCompatibilityAnalyzer()
  val jetifierUsageAnalyzer = JetifierUsageAnalyzer()
  val downloadsAnalyzer = StudioFlags.BUILD_ANALYZER_DOWNLOADS_ANALYSIS.get().ifTrue { DownloadsAnalyzer() }
  val taskCategoryWarningsAnalyzer = TaskCategoryWarningsAnalyzer(taskContainer)

  fun getBuildFinishedTimestamp(): Long {
    return criticalPathAnalyzer.result.buildFinishedTimestamp
  }

  val buildAnalyzers: List<BaseAnalyzer<*>>
    get() = listOfNotNull(
      alwaysRunTasksAnalyzer,
      annotationProcessorsAnalyzer,
      criticalPathAnalyzer,
      noncacheableTasksAnalyzer,
      garbageCollectionAnalyzer,
      projectConfigurationAnalyzer,
      tasksConfigurationIssuesAnalyzer,
      configurationCachingCompatibilityAnalyzer,
      jetifierUsageAnalyzer,
      downloadsAnalyzer,
      taskCategoryWarningsAnalyzer
    )
}