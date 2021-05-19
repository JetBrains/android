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
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WarningsDataPageModelImplTest {

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(this))
  }
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000).apply {
    issues = listOf(TaskIssueUiDataContainer.TaskSetupIssue(this, task1, ""))
    task1.issues = task1.issues + listOf(TaskIssueUiDataContainer.TaskSetupIssue(task1, this, ""))
  }

  val mockData = MockUiData(tasksList = listOf(task1, task2, task3))

  var modelUpdateListenerCallsCount = 0
  val model: WarningsDataPageModel = WarningsDataPageModelImpl(mockData).apply {
    setModelUpdatedListener { modelUpdateListenerCallsCount++ }
  }

  @Test
  fun testInitialSelection() {
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(0)
  }

  @Test
  fun testSelectNode() {
    // Arrange
    val lastChild = model.treeRoot.lastLeaf as WarningsTreeNode

    // Act
    model.selectNode(lastChild)

    // Assert
    assertThat(model.selectedNode).isEqualTo(lastChild)
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |===>com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(1)
  }

  @Test
  fun testDeselectNode() {
    // Arrange
    val lastChild = model.treeRoot.lastLeaf as WarningsTreeNode
    model.selectNode(lastChild)

    // Act
    model.selectNode(null)

    // Assert
    assertThat(model.selectedNode).isNull()
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(2)
  }

  @Test
  fun testSelectByPageId() {
    // Act
    val pageId = WarningsPageId.warning(task2.issues.first())
    model.selectPageById(pageId)

    // Assert
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |===>ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(1)
  }

  @Test
  fun testSelectByNotExistingPageId() {
    // Act
    val nonExistingPageId = WarningsPageId(WarningsPageType.TASK_WARNING_TYPE_GROUP, "does-not-exist")
    model.selectPageById(nonExistingPageId)

    // Assert
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(0)
  }

  @Test
  fun testNoTaskSetupIssuesDetected() {
    // Arrange
    val model = WarningsDataPageModelImpl(MockUiData(tasksList = listOf(task2)))

    // Assert
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:resources
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
  }

  @Test
  fun testNoAnnotationProcessorsWarningsIssuesDetected() {
    // Arrange
    val model = WarningsDataPageModelImpl(MockUiData(tasksList = listOf(task1, task2, task3)).apply {
      annotationProcessors = object : AnnotationProcessorsReport {
        override val nonIncrementalProcessors: List<AnnotationProcessorUiData> = emptyList()
      }
    })

    // Assert
    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |    ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
    """.trimMargin())
  }

  @Test
  fun testTreeHeader() {
    assertThat(model.treeHeaderText).isEqualTo("Warnings - Total: 7, Filtered: 7")
  }

  @Test
  fun testFilterApplySelectedNodeRemains() {
    val pageId = WarningsPageId.warning(task1.issues.first())
    model.selectPageById(pageId)
    modelUpdateListenerCallsCount = 0

    model.filter = WarningsFilter.default().copy(
      showTaskWarningTypes = setOf(TaskIssueType.ALWAYS_RUN_TASKS),
      showAnnotationProcessorWarnings = false
    )

    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  ALWAYS_RUN_TASKS
      |===>ALWAYS_RUN_TASKS-:app:compile
      |    ALWAYS_RUN_TASKS-:app:resources
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(1)
  }

  @Test
  fun testFilterApplySelectedNodeDisappears() {
    val pageId = WarningsPageId.warning(task1.issues.first())
    model.selectPageById(pageId)
    modelUpdateListenerCallsCount = 0

    model.filter = WarningsFilter.default().copy(
      showTaskWarningTypes = setOf(TaskIssueType.TASK_SETUP_ISSUE)
    )

    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  TASK_SETUP_ISSUE
      |    TASK_SETUP_ISSUE-:app:compile
      |    TASK_SETUP_ISSUE-:lib:compile
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(1)
  }

  @Test
  fun testGroupedByPlugin() {
    model.groupByPlugin = true

    assertThat(model.print()).isEqualTo("""
      |ROOT
      |  compiler.plugin
      |    :app:compile
      |    :lib:compile
      |  resources.plugin
      |    :app:resources
      |  ANNOTATION_PROCESSORS
      |    com.google.auto.value.processor.AutoAnnotationProcessor
      |    com.google.auto.value.processor.AutoValueBuilderProcessor
      |    com.google.auto.value.processor.AutoOneOfProcessor
    """.trimMargin())
    assertThat(modelUpdateListenerCallsCount).isEqualTo(1)
  }

  private fun WarningsDataPageModel.print(): String {
    return treeRoot.preorderEnumeration().asSequence().joinToString("\n") {
      if (it is WarningsTreeNode) {
        if (selectedNode?.descriptor?.pageId == it.descriptor.pageId) {
          ">".padStart(it.level * 2, padChar = '=') + it.descriptor.pageId.id
        }
        else {
          "".padStart(it.level * 2) + it.descriptor.pageId.id
        }
      }
      else {
        "ROOT"
      }
    }
  }
}