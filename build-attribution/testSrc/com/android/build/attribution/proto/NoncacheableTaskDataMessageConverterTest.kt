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

import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.proto.converters.NoncacheableTaskDataMessageConverter
import com.google.common.truth.Truth
import org.junit.Test

class NoncacheableTaskDataMessageConverterTest {
  @Test
  fun testNonCacheableTasksAnalyzerResult() {
    val taskDatum = TaskData(
      "task name",
      "project path",
      PluginData(PluginData.PluginType.BUILDSRC_PLUGIN, "id name"),
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val cache = mutableMapOf<String, TaskData>()
    cache[taskDatum.getTaskPath()] = taskDatum
    val result = NoncacheableTasksAnalyzer.Result(listOf(taskDatum))
    val resultMessage = NoncacheableTaskDataMessageConverter.transform(result.noncacheableTasks)
    val resultConverted = NoncacheableTaskDataMessageConverter.construct(resultMessage, cache)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }
}