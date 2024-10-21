/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.gemini.formatForTests
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.MessageEvent.Kind.INFO
import com.intellij.build.events.MessageEvent.Kind.WARNING
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerExtension
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

private class FakeGeminiPluginApi : GeminiPluginApi {
  override val MAX_QUERY_CHARS: Int = Int.MAX_VALUE
  var available = true
  var contextAllowed = true

  var sentPrompt: LlmPrompt? = null
  var displayedText: String? = null
  var requestSource: GeminiPluginApi.RequestSource? = null

  var stagedPrompt: String? = null

  override fun isAvailable() = available
  override fun isContextAllowed(project: Project) = contextAllowed

  override fun isFileExcluded(project: Project, file: VirtualFile) = false

  override fun sendChatQuery(project: Project, prompt: LlmPrompt, displayText: String?, requestSource: GeminiPluginApi.RequestSource) {
    sentPrompt = prompt
    displayedText = displayText
    this.requestSource = requestSource
  }

  override fun stageChatQuery(project: Project, prompt: String, requestSource: GeminiPluginApi.RequestSource) {
    stagedPrompt = prompt
  }
}

class BuildOutputParserWrapperTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent
  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setUp() {
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser, ID)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)

    fakeGeminiPluginApi = FakeGeminiPluginApi()
    ApplicationManager.getApplication().registerExtension(GeminiPluginApi.EP_NAME, fakeGeminiPluginApi, projectRule.project)
  }

  @Test
  fun `test 'Ask Gemini' link is added for ERROR FileMessageEvent`() {
    messageEvent = createFileMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isInstanceOf(BuildIssueEvent::class.java)
      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes).hasSize(1)
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
    }
  }

  @Test
  fun `test 'Ask Gemini' link is added for ERROR MessageEvent`() {
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isInstanceOf(BuildIssueEvent::class.java)
      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes).hasSize(1)
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
    }
  }

  @Test
  fun `test 'Ask Gemini' link is not added for WARNING MessageEvent`() {
    messageEvent = createFileMessageEvent(WARNING)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isNotInstanceOf(BuildIssueEvent::class.java)
    }
  }


  @Test
  fun `test 'Ask Gemini' link is not added for INFO MessageEvent`() {
    messageEvent = createFileMessageEvent(INFO)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isNotInstanceOf(BuildIssueEvent::class.java)
    }
  }

  @Test
  fun `test when StudioBot is not available, 'Ask Gemini' links is not added for ERROR MessageEvent`() {
    fakeGeminiPluginApi.available = false
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isNotInstanceOf(BuildIssueEvent::class.java)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sends query to ChatService when context allowed`() {
    fakeGeminiPluginApi.available = true
    fakeGeminiPluginApi.contextAllowed = true
    whenever(ID.type).thenReturn(ExternalSystemTaskType.RESOLVE_PROJECT)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected = """
            USER
            I'm getting the following error while syncing my project. The error is: !!some error message!!
            ```
            Detailed error message
            ```
            How do I fix this?
        """.trimIndent()
      assertThat(fakeGeminiPluginApi.sentPrompt!!.formatForTests()).isEqualTo(expected)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sends query to ChatService without gradle command`() {
    fakeGeminiPluginApi.available = true
    fakeGeminiPluginApi.contextAllowed = true
    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected = """
            USER
            I'm getting the following error while building my project. The error is: !!some error message!!
            ```
            Detailed error message
            ```
            How do I fix this?
        """.trimIndent()
      assertThat(fakeGeminiPluginApi.sentPrompt!!.formatForTests()).isEqualTo(expected)
    }
  }


  @Test
  fun `test 'Ask Gemini' quick fix stages query to ChatService when context not allowed`() {
    fakeGeminiPluginApi.available = true
    fakeGeminiPluginApi.contextAllowed = false
    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected = """
        I'm getting the following error while building my project. The error is: !!some error message!!
        ```
        Detailed error message
        ```
        How do I fix this?
        """.trimIndent()
      assertThat(fakeGeminiPluginApi.stagedPrompt!!).isEqualTo(expected)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix parses Gradle command from projectId`() {
    fakeGeminiPluginApi.available = true
    fakeGeminiPluginApi.contextAllowed = false

    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR, id = "[-4474:2441] > [Task :app:compileDebugJavaWithJavac]")

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected =
        """
        I'm getting the following error while building my project. The error is: !!some error message!!
        ```
        ${'$'} ./gradlew :app:compileDebugJavaWithJavac
        Detailed error message
        ```
        How do I fix this?
        """.trimIndent()
      assertThat(fakeGeminiPluginApi.stagedPrompt!!).isEqualTo(expected)
    }
  }

  private fun createMessageEvent(
    kind: MessageEvent.Kind,
    group: String = "Compiler",
    message: String = "!!some error message!!",
    detailedMessage: String = "Detailed error message",
    id: Any = ID,
  ): MessageEventImpl {
    return MessageEventImpl(
      id,
      kind,
      group,
      message,
      detailedMessage)
  }

  private fun createFileMessageEvent(
    kind: MessageEvent.Kind,
    group: String = "Compiler",
    message: String = "!!some error message!!",
    detailedMessage: String = "Detailed error message",
    id: Any = ID,
  ): FileMessageEvent {
    val folder = temporaryFolder.newFolder("test")
    return FileMessageEventImpl(
      id,
      kind,
      group,
      message,
      detailedMessage,
      FilePosition(FileUtils.join(folder, "main", "src", "main.java"),1, 1))
  }

  companion object {
    val ID = mock<ExternalSystemTaskId>()
  }
}