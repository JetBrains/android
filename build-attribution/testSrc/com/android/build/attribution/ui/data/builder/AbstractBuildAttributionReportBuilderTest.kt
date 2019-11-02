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

import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.BuildEventsAnalyzersResultsProvider
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData

open class AbstractBuildAttributionReportBuilderTest {

  val applicationPlugin = PluginData(PluginData.PluginType.PLUGIN, "com.android.application")
  val libraryPlugin = PluginData(PluginData.PluginType.PLUGIN, "com.android.library")
  val pluginA = PluginData(PluginData.PluginType.PLUGIN, "pluginA")
  val pluginB = PluginData(PluginData.PluginType.PLUGIN, "pluginB")
  val pluginC = PluginData(PluginData.PluginType.UNKNOWN, "pluginC")
  val buildScriptA = PluginData(PluginData.PluginType.SCRIPT, "buildA.gradle")
  val buildScriptB = PluginData(PluginData.PluginType.SCRIPT, "buildB.gradle")


  /**
   * Mock results provider with default empty values.
   */
  open class MockResultsProvider : BuildEventsAnalyzersResultsProvider {
    override fun getAnnotationProcessorsData(): List<AnnotationProcessorsAnalyzer.AnnotationProcessorData> = emptyList()
    override fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorsAnalyzer.AnnotationProcessorData> = emptyList()
    override fun getCriticalPathDuration(): Long = 0
    override fun getTotalBuildTime(): Long = 0
    override fun getTasksCriticalPath(): List<TaskData> = emptyList()
    override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = emptyList()
    override fun getProjectsConfigurationData(): List<ProjectConfigurationData> = emptyList()
    override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = emptyList()
    override fun getNoncacheableTasks(): List<TaskData> = emptyList()
    override fun getTasksSharingOutput(): List<TasksSharingOutputData> = emptyList()
  }
}