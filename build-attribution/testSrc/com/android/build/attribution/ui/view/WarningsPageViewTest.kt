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

import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.WarningsDataPageModelImpl
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.view.details.WarningsViewDetailPagesFactory
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.tree.TreePathUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import javax.swing.JEditorPane

class WarningsPageViewTest {
  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val disposableRule: DisposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000, taskCategory = TaskCategory.JAVA).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000, taskCategory = TaskCategory.ANDROID_RESOURCES).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(this))
  }
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000, taskCategory = TaskCategory.JAVA).apply {
    issues = listOf(TaskIssueUiDataContainer.TaskSetupIssue(this, task1, ""))
    task1.issues = task1.issues + listOf(TaskIssueUiDataContainer.TaskSetupIssue(task1, this, ""))
  }

  private val data = MockUiData(tasksList = listOf(task1, task2, task3), createTaskCategoryWarning = true)

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  private val model = WarningsDataPageModelImpl(data)

  lateinit var view: WarningsPageView

  @Before
  fun setUp() {
    view = WarningsPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }
  }

  @Test
  @RunsInEdt
  fun testCreateView() {
    assertThat(view.component.name).isEqualTo("warnings-view")
    assertThat(view.treeHeaderLabel.text).isEqualTo(model.treeHeaderText)

    assertThat(view.tree.selectionPath).isNull()
    assertThat((view.detailsPanel.key)).isEqualTo(WarningsPageId.emptySelection)
    Mockito.verifyNoMoreInteractions(mockHandlers)
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
    assertThat(view.detailsPanel.key).isEqualTo(pageIdToSelect)
    Mockito.verifyNoMoreInteractions(mockHandlers)
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

  @Test
  @RunsInEdt
  fun testTaskCategoryDetailsPageHasLinkHandlerRegistered() {
    val page = WarningsViewDetailPagesFactory(
      model, mockHandlers, disposableRule.disposable
    ).createDetailsPage(model.getNodeDescriptorById(WarningsPageId.taskCategory(TaskCategory.ANDROID_RESOURCES))!!)

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().let { content ->
      assertThat(content).hasSize(1)

      val htmlLinksHandler = content.first().hyperlinkListeners.find { it is HtmlLinksHandler } as? HtmlLinksHandler
      assertThat(htmlLinksHandler).isNotNull()

      assertThat(htmlLinksHandler!!.registeredLinkActions.keys).containsExactly(
        "AndroidMigrateToNonTransitiveRClassesAction",
        "NON_TRANSITIVE_R_CLASS"
      )
    }
  }

  @Test
  @RunsInEdt
  fun testTreeNodeDeselectionTriggersActionHandlerCallWithNull() {
    // Arrange
    val nodeToSelect = model.treeRoot.lastLeaf as WarningsTreeNode
    view.tree.selectionPath = TreePathUtil.toTreePath(nodeToSelect)

    // Act
    view.tree.clearSelection()

    // Assert
    Mockito.verify(mockHandlers).warningsTreeNodeSelected(Mockito.isNull())
  }

  @Test
  @RunsInEdt
  fun testEmptyState() {
    val data = MockUiData(tasksList = emptyList()).apply {
      annotationProcessors = object : AnnotationProcessorsReport {
        override val nonIncrementalProcessors = emptyList<AnnotationProcessorUiData>()
      }
      confCachingData = ConfigurationCachingTurnedOn
    }
    val model = WarningsDataPageModelImpl(data)
    view = WarningsPageView(model, mockHandlers, disposableRule.disposable).apply {
      component.size = Dimension(600, 200)
    }

    val fakeUi = FakeUi(view.component)
    fakeUi.layoutAndDispatchEvents()

    assertThat(view.component.components.any { it.isVisible }).isFalse()

    val emptyStatusText = (view.component as JBPanelWithEmptyText).emptyText
    assertThat(emptyStatusText.toStringState()).isEqualTo("""
      java.awt.Rectangle[x=29,y=45,width=542,height=64]
      This build has no warnings. To learn more about its performance, check out these views:| width=542 height=20
      Tasks impacting build duration| width=193 height=20
      Plugins with tasks impacting build duration| width=268 height=20
    """.trimIndent())
    // Try click on row centers. Only second and third rows should react being links.
    fakeUi.clickRelativeTo(view.component, 300, 45 + 10)
    Mockito.verifyNoInteractions(mockHandlers)
    fakeUi.clickRelativeTo(view.component, 300, 45 + 32)
    Mockito.verify(mockHandlers).changeViewToTasksLinkClicked(null)
    fakeUi.clickRelativeTo(view.component, 300, 45 + 55)
    Mockito.verify(mockHandlers).changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.BY_PLUGIN)
  }
}