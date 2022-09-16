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

import org.junit.Test
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.proto.converters.TaskDataMessageConverter
import com.android.ide.common.attribution.TaskCategory
import com.google.common.truth.Truth
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class TaskDataConverterTest {
  @Test
  fun testTaskData() {
    val pluginDatum = PluginData(PluginData.PluginType.BINARY_PLUGIN, "id name")
    val taskDatum = TaskData(
      "task name",
      "project path",
      pluginDatum,
      12345,
      12345,
      TaskData.TaskExecutionMode.FULL,
      listOf("abc", "def", "ghi")
    )
    val pluginCache = mutableMapOf<String, PluginData>()
    pluginCache[pluginDatum.idName] = pluginDatum

    taskDatum.isOnTheCriticalPath = true
    taskDatum.setTaskType("task type")
    taskDatum.setTaskCategories(TaskCategory.TEST, listOf(TaskCategory.AIDL, TaskCategory.APK_PACKAGING, TaskCategory.ART_PROFILE))

    val resultMessage = TaskDataMessageConverter.transform(taskDatum)
    val resultConverted = TaskDataMessageConverter.construct(listOf(resultMessage), pluginCache)
    assertTaskDataAllFieldsEquals(resultConverted.single(), taskDatum)
  }

  /**
   * Tests that there is no exceptions when fields is empty
   */
  @Test
  fun testTaskDataEmpty() {
    val pluginDatum = PluginData(PluginData.PluginType.UNKNOWN, "")
    val taskDatum = TaskData(
      "",
      "",
      pluginDatum,
      0,
      0,
      TaskData.TaskExecutionMode.FULL,
      emptyList()
    )
    val pluginCache = mutableMapOf<String, PluginData>()
    pluginCache[pluginDatum.idName] = pluginDatum

    val resultMessage = TaskDataMessageConverter.transform(taskDatum)
    val resultConverted = TaskDataMessageConverter.construct(listOf(resultMessage), pluginCache)
    assertTaskDataAllFieldsEquals(resultConverted.single(), taskDatum)
  }

  private fun assertTaskDataAllFieldsEquals(actual: TaskData, expected: TaskData) {
    TaskData::class.memberProperties.forEach { member ->
      member.isAccessible = true
      Truth.assertThat(member.get(actual)).isEqualTo(member.get(expected))
    }
  }
}