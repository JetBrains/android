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

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData

open class AbstractBuildAttributionReportBuilderTest {

  val applicationPlugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "com.android.build.gradle.internal.plugins.AppPlugin")
    .apply { recordDisplayName(PluginData.DisplayName("com.android.application", "")) }
  val libraryPlugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "com.android.build.gradle.internal.plugins.LibraryPlugin")
    .apply { recordDisplayName(PluginData.DisplayName("com.android.library", "")) }
  val pluginA = PluginData(PluginData.PluginType.BINARY_PLUGIN, "my.plugin.PluginA").apply { recordDisplayName(PluginData.DisplayName("pluginA", "")) }
  val pluginB = PluginData(PluginData.PluginType.BINARY_PLUGIN, "my.plugin.PluginB").apply { recordDisplayName(PluginData.DisplayName("pluginB", "")) }
  val pluginC = PluginData(PluginData.PluginType.UNKNOWN, "pluginC").apply { recordDisplayName(PluginData.DisplayName("pluginC", "")) }


  /**
   * Mock results provider with default empty values.
   */
  open class MockResultsProvider : BuildEventsAnalysisResult {
    override fun getAnnotationProcessorsData(): List<AnnotationProcessorData> = emptyList()
    override fun getNonIncrementalAnnotationProcessorsData(): List<AnnotationProcessorData> = emptyList()
    override fun getTotalBuildTimeMs(): Long = 0
    override fun getCriticalPathTasks(): List<TaskData> = emptyList()
    override fun getTasksDeterminingBuildDuration(): List<TaskData> = emptyList()
    override fun getPluginsDeterminingBuildDuration(): List<PluginBuildData> = emptyList()
    override fun getTotalConfigurationData(): ProjectConfigurationData = getProjectsConfigurationData().first()
    override fun getProjectsConfigurationData(): List<ProjectConfigurationData> = emptyList()
    override fun getAlwaysRunTasks(): List<AlwaysRunTaskData> = emptyList()
    override fun getNonCacheableTasks(): List<TaskData> = emptyList()
    override fun getAppliedPlugins(): Map<String, List<PluginData>> = emptyMap()
    override fun getConfigurationCachingCompatibility(): ConfigurationCachingCompatibilityProjectResult = NoIncompatiblePlugins(emptyList())
    override fun getTasksSharingOutput(): List<TasksSharingOutputData> = emptyList()
    override fun getGarbageCollectionData(): List<GarbageCollectionData> = emptyList()
    override fun getTotalGarbageCollectionTimeMs(): Long = 0
    override fun getJavaVersion(): Int? = null
    override fun isGCSettingSet(): Boolean? = null
  }

  fun plugin(pluginData: PluginData, duration: Long) = PluginConfigurationData(pluginData, duration)

  fun project(name: String, duration: Long, plugins: List<PluginConfigurationData> = emptyList()) = ProjectConfigurationData(name, duration,
                                                                                                                             plugins,
                                                                                                                             emptyList())
}