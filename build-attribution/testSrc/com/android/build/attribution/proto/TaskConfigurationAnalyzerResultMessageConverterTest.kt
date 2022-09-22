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

import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.google.common.truth.Truth
import org.junit.Test

class TaskConfigurationAnalyzerResultMessageConverterTest {
  @Test
  fun testTasksConfigurationIssuesAnalyzerResult() {
    val taskDatum = TaskData(
      "task name",
      "project path",
      PluginData(PluginData.PluginType.SCRIPT, "id name"),
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val taskData = TasksSharingOutputData(taskDatum.getTaskPath(), listOf(taskDatum))
    val cache = mutableMapOf<String, TaskData>()
    cache[taskDatum.getTaskPath()] = taskDatum
    val result = TasksConfigurationIssuesAnalyzer.Result(listOf(taskData))
    val resultMessage = TaskConfigurationAnalyzerResultMessageConverter.transform(result.tasksSharingOutput)
    val resultConverted = TaskConfigurationAnalyzerResultMessageConverter.construct(resultMessage!!, cache)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }
}