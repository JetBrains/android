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

import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.proto.converters.CriticalPathAnalyzerResultMessageConverter
import com.google.common.truth.Truth
import org.junit.Test

class CriticalPathAnalyzerResultMessageConverterTest {
  @Test
  fun testCriticalPathAnalyzerResult() {
    val taskCache = mutableMapOf<String, TaskData>()
    val pluginCache = mutableMapOf<String, PluginData>()
    val criticalPathData = mutableListOf<TaskData>()
    val pluginDatum = PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")
    val criticalPathDatum = TaskData(
      "task name",
      "project path",
      pluginDatum,
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    taskCache[criticalPathDatum.getTaskPath()] = criticalPathDatum
    pluginCache[pluginDatum.idName] = pluginDatum
    criticalPathData.add(criticalPathDatum)
    val criticalPathAnalyzerResult = CriticalPathAnalyzer.Result(
      criticalPathData,
      listOf(PluginBuildData(pluginDatum, 12345)),
      12345,
      12345
    )
    val criticalPathAnalyzerResultMessage = CriticalPathAnalyzerResultMessageConverter
      .transform(criticalPathAnalyzerResult)
    val criticalPathAnalyzerResultConverted = CriticalPathAnalyzerResultMessageConverter
      .construct(criticalPathAnalyzerResultMessage, taskCache, pluginCache)
    Truth.assertThat(criticalPathAnalyzerResult).isEqualTo(criticalPathAnalyzerResultConverted)
  }
}