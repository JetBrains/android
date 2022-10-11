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
package com.android.build.attribution.utils

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import kotlin.math.max

class BuildAnalysisResultsSnapshotGenerator {
  private val stringBuilder = StringBuilder()
  private var currentNestingPrefix = ""

  fun dump(result: BuildAnalysisResults): String {
    dumpTaskData(result.getTaskMap())
    dumpPluginData(result.getPluginMap())
    return stringBuilder.toString().trimIndent()
  }

  private fun String.smartPad() = this.padEnd(max(30, 10 + this.length / 10 * 10))

  private fun prop(name: String, value: () -> String?) {
    value()?.let {
      appendLn("${name.smartPad()}: $it")
    }
  }

  private fun head(name: String, value: () -> String? = { null }) {
    val v = value()
    appendLn(name.smartPad() + if (v != null) ": $v" else "")
  }

  private fun dumpTaskData(taskMap: Map<String, TaskData>) {
    taskMap.toSortedMap().forEach { (taskPath, taskData) ->
      head("Task") { taskPath }
      nest {
        prop("TaskType") { taskData.taskType }
        prop("PrimaryTaskCategory") { taskData.primaryTaskCategory.name }
        taskData.secondaryTaskCategories.joinToString(",").takeIf {
          it.isNotEmpty()
        }?.let { prop("SecondaryTaskCategories") { it } }
        prop("Plugin") { taskData.originPlugin.toString() }
      }
    }
  }

  private fun dumpPluginData(pluginMap: Map<String, PluginData>) {
    pluginMap.toSortedMap().forEach { (pluginName, pluginData) ->
      head("Plugin") { pluginName }
      nest {
        prop("PluginType") { pluginData.pluginType.name }
        pluginData.displayNames().joinToString(",").takeIf {
          it.isNotEmpty()
        }?.let { prop("PluginDisplayNames") { it } }
      }
    }
  }

  private fun nest(action: BuildAnalysisResultsSnapshotGenerator.() -> Unit) {
    val saved = currentNestingPrefix
    currentNestingPrefix += "    "
    action()
    currentNestingPrefix = saved
  }

  private fun appendLn(data: String) {
    stringBuilder.append(currentNestingPrefix)
    stringBuilder.appendLine(data.trimEnd())
  }
}