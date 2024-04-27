/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.disposable
import com.intellij.build.ExecutionNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.ThreadingAssertions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.function.Supplier
import kotlin.test.assertTrue

@RunsInEdt
class ExplainSyncOrBuildOutputTest {

  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), projectRule, ApplicationServiceRule(IssueExplainer::class.java, TestIssueExplainer), EdtRule())

  @Test
  fun testActionPerformedExplainerText() {
    val panel = createTree()
    panel.setSelectionRow(5)

    val action = ExplainSyncOrBuildOutput()
    val event = AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)
    assertEquals("Unexpected tokens (use ';' to separate expressions on the same line)", TestIssueExplainer.wasCalled)
  }

  @Test
  fun testActionUpdate() {
    val panel = createTree()
    val action = ExplainSyncOrBuildOutput()
    val event = AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    assertTrue(event.presentation.isEnabled)
    // no selection
    action.update(event)
    assertFalse(event.presentation.isEnabled)
    // leaf node selected
    panel.setSelectionRow(5)
    action.update(event)
    assertTrue(event.presentation.isEnabled)
    // inner node selected
    panel.setSelectionRow(4)
    action.update(event)
    assertFalse(event.presentation.isEnabled)
    assertNull(TestIssueExplainer.wasCalled)
  }

  inner class TestDataContext(val panel: Tree) : DataContext {

    override fun getData(dataId: String): Any? {
      throw NotImplementedError()
    }

    override fun <T> getData(key: DataKey<T>): T? {
      @Suppress("UNCHECKED_CAST")
      return when (key) {
        CommonDataKeys.PROJECT -> projectRule.project as T
        PlatformCoreDataKeys.CONTEXT_COMPONENT -> panel as T
        else -> null
      }
    }
  }

  @Test
  fun testActionUpdateThread() {
    assertEquals(ExplainSyncOrBuildOutput().actionUpdateThread, ActionUpdateThread.EDT)
  }


  object TestIssueExplainer : IssueExplainer() {

    var wasCalled: String? = null

    override fun explain(
      project: Project,
      request: String,
      requestKind: RequestKind,
      extraDocumentation: String?,
      extraUrls: List<String>
    ) {
      ThreadingAssertions.assertEventDispatchThread()
      wasCalled = request
    }
  }

  private class TestBuildTreeStructure(val root: TestExecutionNode) : AbstractTreeStructure() {
    override fun getRootElement(): Any = root

    override fun getChildElements(element: Any): Array<Any> = (element as ExecutionNode).childList.toTypedArray()

    override fun getParentElement(element: Any): Any? = (element as ExecutionNode).parent

    override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> = element as ExecutionNode

    override fun commit() {}

    override fun hasSomethingToCommit(): Boolean = false

  }

  private class TestExecutionNode(val payload: String, vararg val myChildren: TestExecutionNode) :
    ExecutionNode(null, null, true, Supplier { true }) {
    override fun getName(): String {
      return payload
    }

    override fun getChildList(): List<TestExecutionNode> = listOf(*myChildren)
  }

  private fun createTree(): Tree {
    val node = TestExecutionNode(
      "",
      TestExecutionNode(
        "failed",
        TestExecutionNode(
          ":app:compileDebugKotlin",
          TestExecutionNode(
            "SomeFile.kt",
            TestExecutionNode(
              "MainActivity.kt",
              TestExecutionNode("Unexpected tokens (use ';' to separate expressions on the same line)")
            )
          )
        )
      )
    )
    val eventDispatchThread = Invoker.forEventDispatchThread(projectRule.disposable)
    val root = StructureTreeModel(TestBuildTreeStructure(node), null, eventDispatchThread, projectRule.disposable)
    val panel = Tree(root)

    for (i in 0 until 6) {
      panel.expandRow(i)
    }
    return panel
  }
}