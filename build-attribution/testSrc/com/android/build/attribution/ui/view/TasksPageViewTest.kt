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
import com.android.build.attribution.ui.model.TaskDetailsPageType
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksDataPageModelImpl
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.tree.TreePathUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Component
import java.awt.Dimension

class TasksPageViewTest {
  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  var disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  val data = MockUiData(tasksList = listOf(task1, task2, task3))

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  private val model = TasksDataPageModelImpl(data)

  lateinit var view: TasksPageView

  @Before
  fun setUp() {
    view = TasksPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }
  }

  @After
  fun clearOverride() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  @RunsInEdt
  fun testCreateView() {
    assertThat(view.component.name).isEqualTo("tasks-view")
    assertThat(view.groupingCheckBox.isSelected).isFalse()
    assertThat(view.tasksGroupingComboBox.selectedItem).isEqualTo(TasksDataPageModel.Grouping.BY_TASK_CATEGORY)
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    assertThat(view.tree.selectionPath).isNull()
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testModelUpdatedWithoutTaskCategoryInfo() {
    val model = TasksDataPageModelImpl(MockUiData(tasksList = listOf(task1, task2, task3), createTaskCategoryInfo = false))
    view = TasksPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }
    // Act - update model by opening Plugin page
    model.selectPageById(TasksPageId(TasksDataPageModel.Grouping.BY_PLUGIN, TaskDetailsPageType.PLUGIN_DETAILS, "resources.plugin"))

    // Assert view updated values from model
    assertThat(view.groupingCheckBox.isSelected).isTrue()
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    val selectedNode = view.tree.selectionPath?.lastPathComponent as TasksTreeNode
    assertThat(selectedNode).isEqualTo(model.selectedNode)
    assertThat(findVisibleDetailsPageNames(view.detailsPanel)).isEqualTo("details-${selectedNode.descriptor.pageId}")
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testModelUpdatedWithTaskCategoryInfo() {
    val model = TasksDataPageModelImpl(MockUiData(tasksList = listOf(task1, task2, task3), createTaskCategoryInfo = true))
    view = TasksPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }

    // Act - update model by opening Plugin page
    model.selectPageById(TasksPageId(TasksDataPageModel.Grouping.BY_PLUGIN, TaskDetailsPageType.PLUGIN_DETAILS, "resources.plugin"))

    // Assert view updated values from model
    assertThat(view.tasksGroupingComboBox.selectedItem).isEqualTo(TasksDataPageModel.Grouping.BY_PLUGIN)
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    val selectedNode = view.tree.selectionPath?.lastPathComponent as TasksTreeNode
    assertThat(selectedNode).isEqualTo(model.selectedNode)
    assertThat(findVisibleDetailsPageNames(view.detailsPanel)).isEqualTo("details-${selectedNode.descriptor.pageId}")
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testGroupingChangeTriggersActionHandler() {
    // Act
    view.groupingCheckBox.doClick()

    // Assert
    Mockito.verify(mockHandlers).tasksGroupingSelectionUpdated(TasksDataPageModel.Grouping.BY_PLUGIN)
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testTreeSelectionTriggersActionHandler() {
    // Arrange
    val nodeToSelect = model.treeRoot.lastLeaf as TasksTreeNode

    // Act
    view.tree.selectionPath = TreePathUtil.toTreePath(nodeToSelect)

    // Assert
    Mockito.verify(mockHandlers).tasksTreeNodeSelected(nodeToSelect)
    Mockito.verifyNoMoreInteractions(mockHandlers)
  }

  @Test
  @RunsInEdt
  fun testTreeNodeDeselectionTriggersActionHandlerCallWithNull() {
    // Arrange
    val nodeToSelect = model.treeRoot.lastLeaf as TasksTreeNode
    view.tree.selectionPath = TreePathUtil.toTreePath(nodeToSelect)

    // Act
    view.tree.clearSelection()

    // Assert
    Mockito.verify(mockHandlers).tasksTreeNodeSelected(Mockito.isNull())
  }

  @Test
  @RunsInEdt
  // Empty state tested here is when there are no tasks to be shown thus tree would be completely empty.
  // It is replaced with special message instead.
  fun testEmptyState() {
    val data = MockUiData(tasksList = emptyList())
    val model = TasksDataPageModelImpl(data)
    view = TasksPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }

    val emptyStatePanel = view.component.components.single()
    assertThat(emptyStatePanel.isVisible).isTrue()
    assertThat(emptyStatePanel.name).isEqualTo("empty-state")
    val links = TreeWalker(emptyStatePanel).descendants().filterIsInstance(HyperlinkLabel::class.java)
    assertThat(links).hasSize(1)

    // Act / assert links handling
    links[0].doClick()
    Mockito.verify(mockHandlers).changeViewToWarningsLinkClicked()
  }

  private fun findVisibleDetailsPageNames(parent: Component): String {
    return TreeWalker(parent).descendants().asSequence()
      .filter { it.name?.startsWith("details-") ?: false }
      .filter { it.isVisible }
      .joinToString(separator = ",") { it.name }
  }
}