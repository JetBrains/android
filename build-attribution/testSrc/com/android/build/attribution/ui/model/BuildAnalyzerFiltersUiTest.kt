/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.model

import com.android.build.attribution.ui.data.PluginSourceType.ANDROID_PLUGIN
import com.android.build.attribution.ui.data.PluginSourceType.BUILD_SCRIPT
import com.android.build.attribution.ui.data.PluginSourceType.THIRD_PARTY
import com.android.build.attribution.ui.data.TaskIssueType
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test

class BuildAnalyzerFiltersUiTest {

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @Test
  fun testWarningsFilterUiShortSummary() {
    fun verifyFilter(filter: WarningsFilter, expectedText: String) {
      expect.that(filter.toUiText()).isEqualTo(expectedText)
    }
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = emptySet(),
        showTaskWarningTypes = emptySet(),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Nothing selected")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN),
        showTaskWarningTypes = emptySet(),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Nothing selected")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN),
        showTaskWarningTypes = emptySet(),
        showAnnotationProcessorWarnings = true,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Annotation processors")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = emptySet(),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Nothing selected")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = emptySet(),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
        showAnnotationProcessorWarnings = true,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Annotation processors")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS, TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN),
        showTaskWarningTypes = setOf(TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY),
        showTaskWarningTypes = setOf(TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT),
        showTaskWarningTypes = setOf(TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "Selected types of task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT),
        showTaskWarningTypes = setOf(TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = true,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = true,
        showJetifierWarnings = true
      ),
      expectedText = "Selected types of task warnings, Annotation processors, Configuration cache, Jetifier")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS, TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = false,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = false,
        showJetifierWarnings = false
      ),
      expectedText = "All task warnings")
    verifyFilter(
      filter = WarningsFilter(
        showTaskSourceTypes = setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT),
        showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS, TaskIssueType.TASK_SETUP_ISSUE),
        showAnnotationProcessorWarnings = true,
        showNonCriticalPathTasks = false,
        showConfigurationCacheWarnings = true,
        showJetifierWarnings = true
      ),
      expectedText = "All task warnings, Annotation processors, Configuration cache, Jetifier"
    )
  }

  @Test
  fun testTasksFilterUiShortSummary() {
    expect.that(TasksFilter(emptySet(), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("No task types selected")
    expect.that(TasksFilter(emptySet(), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("No task types selected")

    expect.that(TasksFilter(setOf(ANDROID_PLUGIN), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("Android/Java/Kotlin tasks")
    expect.that(TasksFilter(setOf(BUILD_SCRIPT), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("Project customization tasks")
    expect.that(TasksFilter(setOf(THIRD_PARTY), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("Other tasks")

    expect.that(TasksFilter(setOf(ANDROID_PLUGIN), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("Android/Java/Kotlin tasks with warnings")
    expect.that(TasksFilter(setOf(BUILD_SCRIPT), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("Project customization tasks with warnings")
    expect.that(TasksFilter(setOf(THIRD_PARTY), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("Other tasks with warnings")

    expect.that(TasksFilter(setOf(ANDROID_PLUGIN, BUILD_SCRIPT), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("Android/Java/Kotlin, Project customization tasks")
    expect.that(TasksFilter(setOf(THIRD_PARTY, BUILD_SCRIPT), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("Project customization, Other tasks")
    expect.that(TasksFilter(setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT), showTasksWithoutWarnings = true).toUiText())
      .isEqualTo("All tasks")

    expect.that(TasksFilter(setOf(ANDROID_PLUGIN, BUILD_SCRIPT), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("Android/Java/Kotlin, Project customization tasks with warnings")
    expect.that(TasksFilter(setOf(THIRD_PARTY, BUILD_SCRIPT), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("Project customization, Other tasks with warnings")
    expect.that(TasksFilter(setOf(ANDROID_PLUGIN, THIRD_PARTY, BUILD_SCRIPT), showTasksWithoutWarnings = false).toUiText())
      .isEqualTo("All tasks with warnings")
  }
}