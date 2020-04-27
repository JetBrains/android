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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.WarningsDataPageModelImpl
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.tree.TreePathUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension

class WarningsPageViewTest {
  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val edtRule = EdtRule()

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

  val data = MockUiData(tasksList = listOf(task1, task2, task3))

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  private val model = WarningsDataPageModelImpl(data)

  lateinit var view: WarningsPageView

  @Before
  fun setUp() {
    view = WarningsPageView(model, mockHandlers).apply {
      component.size = Dimension(600, 200)
    }
  }

  @Test
  @RunsInEdt
  fun testCreateView() {
    assertThat(model.modelUpdatedListener).isNotNull()
    assertThat(view.component.name).isEqualTo("warnings-view")
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    val selectedNode = view.tree.selectionPath?.lastPathComponent as WarningsTreeNode
    assertThat(selectedNode).isEqualTo(model.selectedNode)
    assertThat((view.detailsPanel.key)).isEqualTo(model.selectedNode?.descriptor)
    Mockito.verifyZeroInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testModelUpdated() {
    // Act - update model by opening Plugin page
    val pageIdToSelect = WarningsPageId.warning(task2.issues.first())
    model.selectPageById(pageIdToSelect)

    // Assert view updated values from model
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    val selectedNode = view.tree.selectionPath?.lastPathComponent as WarningsTreeNode
    assertThat(selectedNode).isEqualTo(model.selectedNode)
    assertThat(selectedNode.descriptor.pageId).isEqualTo(pageIdToSelect)
    assertThat((view.detailsPanel.key.pageId)).isEqualTo(pageIdToSelect)
    Mockito.verifyZeroInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testTreeSelectionTriggersActionHandler() {
    // Arrange
    val nodeToSelect = model.treeRoot.lastLeaf as WarningsTreeNode

    // Act
    view.tree.selectionPath = TreePathUtil.toTreePath(nodeToSelect)

    // Assert
    Mockito.verify(mockHandlers).warningsTreeNodeSelected(nodeToSelect)
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }
}