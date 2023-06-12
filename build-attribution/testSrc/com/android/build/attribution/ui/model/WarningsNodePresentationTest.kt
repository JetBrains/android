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
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class WarningsNodePresentationTest {

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }

  val data = MockUiData(tasksList = listOf(task1))

  @Test
  fun testTaskWarningPresentation() {
    val descriptor = TaskWarningDetailsNodeDescriptor(task1.issues.first())

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:compile",
      suffix = "",
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = "2.0s"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskWarningTypeRootPresentation() {
    val taskIssuesGroup = data.issues.first()
    val descriptor = TaskWarningTypeNodeDescriptor(taskIssuesGroup.type, taskIssuesGroup.issues)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "Always-Run Tasks",
      suffix = "1 warning",
      nodeIconState = NodeIconState.NO_ICON,
      rightAlignedSuffix = "2.0s"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testAnnotationProcessorsRootPresentation() {
    val descriptor = AnnotationProcessorsRootNodeDescriptor(data.annotationProcessors)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "Non-incremental Annotation Processors",
      suffix = "3 warnings",
      rightAlignedSuffix = "1.4s",
      nodeIconState = NodeIconState.NO_ICON
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testAnnotationProcessorNodePresentation() {
    val descriptor = AnnotationProcessorDetailsNodeDescriptor(data.annotationProcessors.nonIncrementalProcessors.first())

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "com.google.auto.value.processor.AutoAnnotationProcessor",
      suffix = "",
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = "0.1s"
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }
}
