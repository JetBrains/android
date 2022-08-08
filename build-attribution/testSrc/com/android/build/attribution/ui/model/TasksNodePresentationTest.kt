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

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class TasksNodePresentationTest {

  val timeDistributionBuilder = TimeDistributionBuilder()

  @Test
  fun testTaskWithoutWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200, 10000)
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.UNGROUPED, timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(task.executionTime.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      nodeIconState = NodeIconState.EMPTY_PLACEHOLDER,
      rightAlignedSuffix = "1.2s 12.0%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskWithWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200, 10000)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.UNGROUPED, timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(task.executionTime.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = "1.2s 12.0%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testPluginWithoutWarningPresentation() {
    val mockUiData = MockUiData(criticalPathDurationMs = 1000)
    val task = mockUiData.mockTask(":app", "resources", "resources.plugin", 855)
    val plugin = mockUiData.createPluginData("resources.plugin", listOf(task))

    val descriptor = EntryDetailsNodeDescriptor(plugin, listOf(task), timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(plugin.criticalPathDuration.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "resources.plugin",
      suffix = "",
      nodeIconState = NodeIconState.NO_ICON,
      rightAlignedSuffix = "0.9s 85.5%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testPluginWithWarningPresentation() {
    val mockUiData = MockUiData(criticalPathDurationMs = 10000)
    val task = mockUiData.mockTask(":app", "resources", "resources.plugin", 840)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val plugin = mockUiData.createPluginData("resources.plugin", listOf(task))

    val descriptor = EntryDetailsNodeDescriptor(plugin, listOf(task), timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(plugin.criticalPathDuration.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "resources.plugin",
      suffix = "1 warning",
      nodeIconState = NodeIconState.NO_ICON,
      rightAlignedSuffix = "0.8s  8.4%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskUnderPluginWithoutWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200, 10000)
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.BY_PLUGIN, timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(task.executionTime.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      nodeIconState = NodeIconState.EMPTY_PLACEHOLDER,
      rightAlignedSuffix = "1.2s 12.0%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskUnderPluginWithWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200, 10000)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.BY_PLUGIN, timeDistributionBuilder)

    timeDistributionBuilder.registerTimeEntry(task.executionTime.supplement().timeMs)
    timeDistributionBuilder.seal()

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = "1.2s 12.0%"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }
}
