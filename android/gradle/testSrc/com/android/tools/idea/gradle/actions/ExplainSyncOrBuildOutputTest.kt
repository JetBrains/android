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

import com.android.tools.idea.gradle.actions.ExplainSyncOrBuildOutput.Companion.getErrorDetails
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.StudioBotExternalFlags
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.util.toIoFile
import com.intellij.build.ExecutionNode
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.build.events.FailureResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.impl.FailureImpl
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerExtension
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.Invoker
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.util.function.Supplier
import kotlin.test.assertTrue

@RunsInEdt
class ExplainSyncOrBuildOutputTest {

  private class MockStudioBot : StudioBot.StubStudioBot() {
    var available = true
    var contextAllowed = true

    override fun isAvailable() = available

    override fun isContextAllowed(project: Project) = contextAllowed

    private val _chatService = spy(object : ChatService.StubChatService() {})

    override fun chat(project: Project): ChatService = _chatService
  }

  class MockStudioBotExternalFlags : StudioBotExternalFlags {
    override fun isCompilerErrorContextEnabled(): Boolean = isCompilerErrorContextEnabled

    companion object {
      var isCompilerErrorContextEnabled = true
    }
  }

  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  @get:Rule val tempDirRule = TemporaryDirectoryRule()

  @get:Rule
  val rule =
    RuleChain(
      ApplicationRule(),
      projectRule,
      ApplicationServiceRule(StudioBot::class.java, MockStudioBot()),
      EdtRule(),
    )

  @Before
  fun setUp() {
    MockStudioBotExternalFlags.isCompilerErrorContextEnabled = true

    ApplicationManager.getApplication().registerExtension(
      StudioBotExternalFlags.EP_NAME,
      MockStudioBotExternalFlags(),
      projectRule.disposable
    )
  }

  @Test
  fun testActionPerformedExplainerText() {
    val panel = createTree()
    panel.setSelectionRow(4)

    val action = ExplainSyncOrBuildOutput()
    val event =
      AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(
        buildPrompt(project) {
          userMessage {
            text(
              """
I'm getting an error trying to build my project. The error is "Unexpected tokens (use ';' to separate expressions on the same line)".

Full error details/trace:
MyFile.kt:2:18 Unexpected tokens (use ';' to separate expressions on the same line). This is some extra description.

The error is in this file:
${errorFile.path}

The error is located at the line marked with --->:
```
     class MyClass {
--->   println("Hello") return
     }
```

Explain this error and how to fix it.
      """
                .trimIndent(),
              listOf(errorFile),
            )
          }
        },
        StudioBot.RequestSource.BUILD,
        "Explain build error: Unexpected tokens (use ';' to separate expressions on the same line)",
      )
  }

  @Test
  fun test336196316() {
    // Make sure that multi-line error messages don't mess up the prompt formatting
    val multilineErrorMessage = "Unexpected tokens (use ';' to separate expressions on the same line)\n" +
                                "This is another line of the error description.\n" +
                                "Multiple lines shouldn't mess up the query format."
    val panel = createTree(multilineErrorMessage)
    panel.setSelectionRow(4)

    val action = ExplainSyncOrBuildOutput()
    val event =
      AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(
        buildPrompt(project) {
          userMessage {
            text(
              """
I'm getting an error trying to build my project. The error is "Unexpected tokens (use ';' to separate expressions on the same line)
This is another line of the error description.
Multiple lines shouldn't mess up the query format.".

Full error details/trace:
MyFile.kt:2:18 Unexpected tokens (use ';' to separate expressions on the same line). This is some extra description.

The error is in this file:
${errorFile.path}

The error is located at the line marked with --->:
```
     class MyClass {
--->   println("Hello") return
     }
```

Explain this error and how to fix it.
      """
                .trimIndent(),
              listOf(errorFile),
            )
          }
        },
        StudioBot.RequestSource.BUILD,
        "Explain build error: $multilineErrorMessage"
      )
  }

  @Test
  fun testActionPerformedWithContextSettingDisabled() {
    (StudioBot.getInstance() as MockStudioBot).contextAllowed = false
    val panel = createTree()
    panel.setSelectionRow(4)

    val action = ExplainSyncOrBuildOutput()
    val event =
      AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)
    verify(StudioBot.getInstance().chat(project))
      .stageChatQuery(
        """
          Explain build error: MyFile.kt:2:18 Unexpected tokens (use ';' to separate expressions on the same line). This is some extra description.
        """.trimIndent(),
        StudioBot.RequestSource.BUILD,
      )
  }

  @Test
  fun testActionPerformedWithCompilerErrorContextFlagDisabled() {
    MockStudioBotExternalFlags.isCompilerErrorContextEnabled = false
    val panel = createTree()
    panel.setSelectionRow(4)

    val action = ExplainSyncOrBuildOutput()
    val event =
      AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    action.actionPerformed(event)

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(
        buildPrompt(project) {
          userMessage {
            text(
              """
I'm getting an error trying to build my project. The error is "Unexpected tokens (use ';' to separate expressions on the same line)".

Full error details/trace:
MyFile.kt:2:18 Unexpected tokens (use ';' to separate expressions on the same line). This is some extra description.

Explain this error and how to fix it.
      """
                .trimIndent(),
              emptyList(),
            )
          }
        },
        StudioBot.RequestSource.BUILD,
        "Explain build error: Unexpected tokens (use ';' to separate expressions on the same line)",
      )
    MockStudioBotExternalFlags.isCompilerErrorContextEnabled = true
  }

  @Test
  fun testActionUpdate() {
    val panel = createTree()
    val action = ExplainSyncOrBuildOutput()
    val event =
      AnActionEvent.createFromDataContext("AnActionEvent", Presentation(), TestDataContext(panel))

    assertTrue(event.presentation.isEnabled)
    // no selection
    action.update(event)
    assertFalse(event.presentation.isEnabled)
    // leaf node selected
    panel.setSelectionRow(4)
    action.update(event)
    assertTrue(event.presentation.isEnabled)
    // inner node selected
    panel.setSelectionRow(3)
    action.update(event)
    assertFalse(event.presentation.isEnabled)
    verifyNoInteractions(StudioBot.getInstance().chat(project))
  }

  inner class TestDataContext(val panel: Tree) : DataContext {

    override fun getData(dataId: String): Any? {
      throw NotImplementedError()
    }

    override fun <T> getData(key: DataKey<T>): T? {
      @Suppress("UNCHECKED_CAST")
      return when (key) {
        CommonDataKeys.PROJECT -> project as T
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
    val event =
      object : MessageEventResult {
        override fun getKind() = MessageEvent.Kind.INFO

        override fun getDetails(): String =
          """
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
      Affected Modules: <a href="openFile:/Users/someUsername/AndroidStudioProjects/MyApplicationHedgehog/app/build.gradle.kts">app</a>
      <a href="explain.issue">>> Ask Gemini</a>
    """
            .trimIndent()
      }
    assertEquals(
      """
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
      Affected Modules: <a href="openFile:/Users/someUsername/AndroidStudioProjects/MyApplicationHedgehog/app/build.gradle.kts">app</a>

      """
        .trimIndent(),
      getErrorDetails(event),
    )
  }

  @Test
  fun getErrorDetailsForFailureResult() {
    val node =
      object : ExecutionNode(null, null, false, Supplier { true }) {
        override fun getResult(): EventResult = FailureResult {
          listOf(
            FailureImpl(
              "Gradle Sync issues.",
              """
            Could not find com.android.tools.build:gradle:123.456.789.
            Searched in the following locations:
              - file:/Users/username/dev/studio-main/out/repo/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
              - https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
              - https://repo.maven.apache.org/maven2/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
            Required by:
                unspecified:unspecified:unspecified
            <a href="add.google.maven.repo">Add google Maven repository and sync project</a>
            <a href="open.plugin.build.file">Open File</a>
          """
                .trimIndent(),
            )
          )
        }
      }
    assertEquals(
      """
        Gradle Sync issues.
        Could not find com.android.tools.build:gradle:123.456.789.
        Searched in the following locations:
          - file:/Users/username/dev/studio-main/out/repo/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
          - https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
          - https://repo.maven.apache.org/maven2/com/android/tools/build/gradle/123.456.789/gradle-123.456.789.pom
        Required by:
            unspecified:unspecified:unspecified
        <a href="add.google.maven.repo">Add google Maven repository and sync project</a>
        <a href="open.plugin.build.file">Open File</a>
      """
        .trimIndent(),
      getErrorDetails(node.result),
    )
  }

  @Test
  fun getErrorDetailsForMessageEventResult() {
    val node =
      object : ExecutionNode(null, null, false, Supplier { true }) {
        override fun getResult(): EventResult =
          object : MessageEventResult {
            override fun getKind() = MessageEvent.Kind.ERROR

            override fun getDetails() =
              "e: file:///Users/someUsername/AndroidStudioProjects/MyComposeApp/app/src/main/java/com/example/mycomposeapp/Sandbox.kt:4:5 Expecting member declaration"
          }
      }
    assertEquals(
      """
        e: file:///Users/someUsername/AndroidStudioProjects/MyComposeApp/app/src/main/java/com/example/mycomposeapp/Sandbox.kt:4:5 Expecting member declaration
      """
        .trimIndent(),
      getErrorDetails(node.result),
    )
  }

  private class TestBuildTreeStructure(val root: TestExecutionNode) : AbstractTreeStructure() {
    override fun getRootElement(): Any = root

    override fun getChildElements(element: Any): Array<Any> =
      (element as ExecutionNode).childList.toTypedArray()

    override fun getParentElement(element: Any): Any? = (element as ExecutionNode).parent

    override fun createDescriptor(
      element: Any,
      parentDescriptor: NodeDescriptor<*>?,
    ): NodeDescriptor<*> = element as ExecutionNode

    override fun commit() {}

    override fun hasSomethingToCommit(): Boolean = false
  }

  private class TestExecutionNode(
    private val nodeName: String,
    private val errorDetails: String? = null,
    private val navigatable: Navigatable? = null,
    private val child: TestExecutionNode? = null
  ) :
    ExecutionNode(null, null, true, Supplier { true }) {
    override fun getName(): String = nodeName

    override fun getResult() = object : MessageEventResult {
      override fun getKind() = MessageEvent.Kind.ERROR
      override fun getDetails(): String? = errorDetails
    }

    override fun getNavigatables() = listOfNotNull(navigatable)

    override fun getChildList(): List<TestExecutionNode> = listOfNotNull(child)
  }

  // The file that the error is located in. The path changes on each test run so it needs to be updated
  lateinit var errorFile: VirtualFile

  private fun createTree(
    errorMessage: String = "Unexpected tokens (use ';' to separate expressions on the same line)",
    description: String = "MyFile.kt:2:18 Unexpected tokens (use ';' to separate expressions on the same line). This is some extra description."
  ): Tree {
    errorFile = tempDirRule.createVirtualFile(
      "MyFile.kt",
      """
        class MyClass {
          println("Hello") return
        }
      """
        .trimIndent()
    )

    val navigatable = FileNavigatable(project, FilePosition(errorFile.toIoFile(), 1, 2))

    val node =
      TestExecutionNode(
        "",
        child = TestExecutionNode(
          "failed",
          child = TestExecutionNode(
            ":app:compileDebugKotlin",
            child = TestExecutionNode(
              "MyFile.kt",
              child = TestExecutionNode(
                errorMessage,
                description,
                navigatable
              ),
            ),
          ),
        ),
      )

    val eventDispatchThread = Invoker.forEventDispatchThread(projectRule.disposable)
    val root =
      StructureTreeModel(
        TestBuildTreeStructure(node),
        null,
        eventDispatchThread,
        projectRule.disposable,
      )
    val panel = Tree(root)

    for (i in 0 until 6) {
      panel.expandRow(i)
    }
    return panel
  }
}
