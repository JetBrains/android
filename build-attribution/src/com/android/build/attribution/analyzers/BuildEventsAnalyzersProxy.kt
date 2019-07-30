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

import com.android.build.attribution.BuildAttributionWarningsFilter

/**
 * A way of interaction between the build events analyzers and the build attribution manager.
 * Used to fetch the final data from the analyzers after the build is complete.
 */
class BuildEventsAnalyzersProxy(warningsFilter: BuildAttributionWarningsFilter) {
  private val alwaysRunTasksAnalyzer = AlwaysRunTasksAnalyzer(warningsFilter)
  private val annotationProcessorsAnalyzer = AnnotationProcessorsAnalyzer(warningsFilter)
  private val criticalPathAnalyzer = CriticalPathAnalyzer(warningsFilter)
  private val projectConfigurationAnalyzer = ProjectConfigurationAnalyzer(warningsFilter)

  fun getAnalyzers(): List<BuildEventsAnalyzer> = listOf(alwaysRunTasksAnalyzer,
                                                         annotationProcessorsAnalyzer,
                                                         criticalPathAnalyzer,
                                                         projectConfigurationAnalyzer)

  fun getAnnotationProcessorsData(): List<AnnotationProcessorsAnalyzer.AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.getAnnotationProcessorsData()
  }

  fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorsAnalyzer.AnnotationProcessorData> {
    return annotationProcessorsAnalyzer.getNonIncrementalAnnotationProcessorsData()
  }

  fun getCriticalPathDuration(): Long {
    return criticalPathAnalyzer.criticalPathDuration
  }

  fun getTasksCriticalPath(): List<CriticalPathAnalyzer.TaskBuildData> {
    return criticalPathAnalyzer.tasksCriticalPath
  }

  fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> {
    return criticalPathAnalyzer.pluginsCriticalPath
  }

  fun getProjectsConfigurationData(): List<ProjectConfigurationAnalyzer.ProjectConfigurationData> {
    return projectConfigurationAnalyzer.projectsConfigurationData
  }

  fun getPluginsSlowingConfiguration(): List<ProjectConfigurationAnalyzer.ProjectConfigurationData> {
    return projectConfigurationAnalyzer.pluginsSlowingConfiguration
  }

  fun getAlwaysRunTasks(): List<AlwaysRunTasksAnalyzer.AlwaysRunTaskData> {
    return alwaysRunTasksAnalyzer.getAlwaysRunTasks()
  }
}
