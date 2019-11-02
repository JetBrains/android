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

/*
 * The set of interfaces in this file represents the build attribution report data model and is used for any data access from the UI.
 * These interfaces correspond to the tree structure of UI info panels and contain all data required for presentation.
 *
 * All UI report implementations should get data from these interfaces and should never depend on any of the backend classes directly.
 * This model should provide data in a state that can be directly presented on the UI without further processing
 * (e.g. should be properly sorted).
 */

interface BuildAttributionReportUiData {
  val buildSummary: BuildSummary
  val criticalPathTasks: CriticalPathTasksUiData
  val criticalPathPlugins: CriticalPathPluginsUiData
}

interface BuildSummary {
  val buildFinishedTimestamp: Long
  val totalBuildDuration: TimeWithPercentage
  val criticalPathDuration: TimeWithPercentage
}

interface CriticalPathTasksUiData {
  val criticalPathDuration: TimeWithPercentage
  val miscStepsTime: TimeWithPercentage
  val tasks: List<TaskUiData>
  val size: Int
    get() = tasks.size
}

interface CriticalPathPluginsUiData {
  val criticalPathDuration: TimeWithPercentage
  val miscStepsTime: TimeWithPercentage
  val plugins: List<CriticalPathPluginUiData>
}

interface TaskUiData {
  val module: String
  val taskPath: String
  val taskType: String
  val executionTime: TimeWithPercentage
  val executedIncrementally: Boolean
  val onCriticalPath: Boolean
  val pluginName: String
  val sourceType: PluginSourceType
  val reasonsToRun: List<String>
}

enum class PluginSourceType {
  ANDROID_PLUGIN, BUILD_SRC, THIRD_PARTY
}

interface CriticalPathPluginUiData {
  val name: String
  /** Total time of this plugin tasks on critical path. */
  val criticalPathDuration: TimeWithPercentage
  /** This plugin tasks on critical path. */
  val criticalPathTasks: List<TaskUiData>
}
