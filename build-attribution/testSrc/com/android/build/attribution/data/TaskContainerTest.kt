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
package com.android.build.attribution.data

import com.android.build.attribution.analyzers.createBinaryPluginIdentifierStub
import com.android.build.attribution.analyzers.createTaskFinishEventStub
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class TaskContainerTest {

  private val data = AndroidGradlePluginAttributionData(
     taskNameToTaskInfoMap = mapOf("a" to AndroidGradlePluginAttributionData.TaskInfo(
       className = "b",
       taskCategoryInfo = AndroidGradlePluginAttributionData.TaskCategoryInfo(
         primaryTaskCategory = TaskCategory.ANDROID_RESOURCES,
         secondaryTaskCategories = listOf(
           TaskCategory.COMPILATION,
           TaskCategory.SOURCE_PROCESSING
         ))))
  )

  @After
  fun clearOverride() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  fun taskCategoriesAreSetWhenFlagIsEnabled() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val pluginA = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val sampleTask = createTaskFinishEventStub(":a", pluginA, emptyList(), 0, 0 )
    taskContainer.getTask(sampleTask, pluginContainer)
    taskContainer.updateTasksData(data)
    assertThat(taskContainer.getTask(":a")?.primaryTaskCategory).isEqualTo(TaskCategory.ANDROID_RESOURCES)
    assertThat(taskContainer.getTask(":a")?.secondaryTaskCategories).containsExactlyElementsIn(listOf(TaskCategory.COMPILATION, TaskCategory.SOURCE_PROCESSING))
  }

  @Test
  fun primaryTaskCategoryForUnknownPluginIsUnknown() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val pluginA = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val sampleTask = createTaskFinishEventStub(":sampleTask", pluginA, emptyList(), 0, 0 )
    taskContainer.getTask(sampleTask, pluginContainer)
    taskContainer.updateTasksData(data)
    assertThat(taskContainer.getTask(":sampleTask")?.primaryTaskCategory).isEqualTo(TaskCategory.UNCATEGORIZED)
  }

  @Test
  fun taskCategoriesAreNotSetWhenFlagIsDisabled() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(false)
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val pluginA = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val sampleTask = createTaskFinishEventStub(":sampleTask", pluginA, emptyList(), 0, 0 )
    taskContainer.getTask(sampleTask, pluginContainer)
    taskContainer.updateTasksData(data)
    assertThat(taskContainer.getTask(":sampleTask")?.primaryTaskCategory).isEqualTo(TaskCategory.UNCATEGORIZED)
  }
}