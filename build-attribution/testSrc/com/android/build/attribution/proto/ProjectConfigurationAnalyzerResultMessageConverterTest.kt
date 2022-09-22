/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.proto

import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.google.common.truth.Truth
import org.junit.Test

class ProjectConfigurationAnalyzerResultMessageConverterTest {
  @Test
  fun testProjectConfigurationAnalyzerResult() {
    val pluginsConfigurationDataMap = mutableMapOf<PluginData, Long>()
    val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
    val allAppliedPlugins = mutableMapOf<String, List<PluginData>>()
    pluginsConfigurationDataMap[PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")] = 12345
    projectConfigurationData.add(ProjectConfigurationData("project path", 12345, listOf(), listOf()))
    allAppliedPlugins["id"] = listOf(PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name"))
    val result = ProjectConfigurationAnalyzer.Result(pluginsConfigurationDataMap, projectConfigurationData, allAppliedPlugins)
    val resultMessage = ProjectConfigurationAnalyzerResultMessageConverter.transform(result)
    val resultConverted = ProjectConfigurationAnalyzerResultMessageConverter.construct(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }
}