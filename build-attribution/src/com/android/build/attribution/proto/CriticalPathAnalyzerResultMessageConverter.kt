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

import com.android.build.attribution.BuildAnalysisResultsMessage.CriticalPathAnalyzerResult
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData

class CriticalPathAnalyzerResultMessageConverter {
  companion object {
    fun transform(criticalPathAnalyzerData: CriticalPathAnalyzer.Result): CriticalPathAnalyzerResult =
      CriticalPathAnalyzerResult.newBuilder()
        .addAllTaskIdsDeterminingBuildDuration(criticalPathAnalyzerData.tasksDeterminingBuildDuration.map { it.getTaskPath() })
        .addAllPluginsDeterminingBuildDuration(criticalPathAnalyzerData.pluginsDeterminingBuildDuration.map(::transformPluginBuildData))
        .setBuildFinishedTimestamp(criticalPathAnalyzerData.buildFinishedTimestamp)
        .setBuildStartedTimestamp(criticalPathAnalyzerData.buildStartedTimestamp)
        .build()

    fun construct(criticalPathAnalyzerResult: CriticalPathAnalyzerResult,
                  tasks: MutableMap<String, TaskData>,
                  plugins: MutableMap<String, PluginData>)
      : CriticalPathAnalyzer.Result {
      val tasksDeterminingBuildDuration = criticalPathAnalyzerResult.taskIdsDeterminingBuildDurationList.map { tasks[it] }
      val pluginsDeterminingBuildDuration = mutableListOf<PluginBuildData>()
      criticalPathAnalyzerResult.pluginsDeterminingBuildDurationList.forEach {
        pluginsDeterminingBuildDuration.add(PluginBuildData(PluginData(plugins[it.pluginID]!!.pluginType, it.pluginID), it.buildDuration))
      }
      return CriticalPathAnalyzer.Result(
        tasksDeterminingBuildDuration.mapNotNull { it },
        pluginsDeterminingBuildDuration,
        criticalPathAnalyzerResult.buildStartedTimestamp,
        criticalPathAnalyzerResult.buildFinishedTimestamp
      )
    }

    private fun transformPluginBuildData(pluginBuildData: PluginBuildData) =
      CriticalPathAnalyzerResult.PluginBuildData.newBuilder()
        .setBuildDuration(pluginBuildData.buildDuration)
        .setPluginID(pluginBuildData.plugin.idName)
        .build()
  }
}