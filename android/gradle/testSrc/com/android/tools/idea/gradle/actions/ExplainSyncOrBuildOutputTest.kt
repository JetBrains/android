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

import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.disposable
import com.intellij.build.ExecutionNode
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.Invoker
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.function.Supplier
import kotlin.test.assertTrue

@RunsInEdt
class ExplainSyncOrBuildOutputTest {

  private val projectRule = ProjectRule()

  private val testStudioBot =
      object : StudioBot.StubStudioBot() {
        var wasCalled: String? = null

        override fun isAvailable(): Boolean = true

        override fun isContextAllowed(): Boolean = true

        override fun chat(project: Project): ChatService = object : ChatService {
          override fun sendChatQuery(query: AiExcludeService.ValidatedQuery,
                                     requestSource: StudioBot.RequestSource,
                                     displayText: String) {
            ApplicationManager.getApplication().assertIsDispatchThread()
            wasCalled = query.query
          }
        }
      }

  @get:Rule
  val rule = RuleChain(ApplicationRule(), projectRule, ApplicationServiceRule(StudioBot::class.java, testStudioBot), EdtRule())

  @Test
  fun testActionPerformedExplainerText() {
    val panel = createTree()
    panel.setSelectionRow(5)

    val action = ExplainSyncOrBuildOutput()
    val event = AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)
    assertEquals("Explain build error: Unexpected tokens (use ';' to separate expressions on the same line)", testStudioBot.wasCalled)
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
    assertNull(testStudioBot.wasCalled)
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

  @Test
  fun testGetErrorDescription() {
    val event = object : MessageEventResult {
      override fun getKind() = MessageEvent.Kind.INFO

      override fun getDetails(): String = """
      We recommend using a newer Android Gradle plugin to use compileSdk = 34456

      This Android Gradle plugin (8.1.2) was tested up to compileSdk = 34.

      You are strongly encouraged to update your project to use a newer
      Android Gradle plugin that has been tested with compileSdk = 34456.

      If you are already using the latest version of the Android Gradle plugin,
      you may need to wait until a newer version with support for compileSdk = 34456 is available.

      To suppress this warning, add/update
          android.suppressUnsupportedCompileSdk=34456
      to this project's gradle.properties.
      <a href="android.suppressUnsupportedCompileSdk">Update Gradle property to suppress warning</a>
      Affected Modules: <a href="openFile:/Users/baskakov/AndroidStudioProjects/MyApplicationHedgehog/app/build.gradle.kts">app</a>
      <a href="explain.issue">>> Ask Studio Bot</a>
    """.trimIndent()
    }
    assertEquals("""
      We recommend using a newer Android Gradle plugin to use compileSdk = 34456

      This Android Gradle plugin (8.1.2) was tested up to compileSdk = 34.

      You are strongly encouraged to update your project to use a newer
      Android Gradle plugin that has been tested with compileSdk = 34456.

      If you are already using the latest version of the Android Gradle plugin,
      you may need to wait until a newer version with support for compileSdk = 34456 is available.

      To suppress this warning, add/update
          android.suppressUnsupportedCompileSdk=34456
      to this project's gradle.properties.
      <a href="android.suppressUnsupportedCompileSdk">Update Gradle property to suppress warning</a>
      Affected Modules: <a href="openFile:/Users/baskakov/AndroidStudioProjects/MyApplicationHedgehog/app/build.gradle.kts">app</a>
      
      """.trimIndent(), getErrorShortDescription(event))
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