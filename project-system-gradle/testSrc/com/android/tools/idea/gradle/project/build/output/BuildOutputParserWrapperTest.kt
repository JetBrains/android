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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
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
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class BuildOutputParserWrapperTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent

  @Before
  fun setUp() {
    // Set StudioBot's availability to true by default.
    setStudioBotInstanceAvailability(true)
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser, ID)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)
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
    setStudioBotInstanceAvailability(false)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      assertThat(event).isNotInstanceOf(BuildIssueEvent::class.java)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sends query to ChatService when context allowed`() {
    // Given: Context is allowed.
    setStudioBotInstanceAvailability(isAvailable = true, isContextAllowed = true)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.RESOLVE_PROJECT)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      verify(StudioBot.getInstance().chat(projectRule.project)).sendChatQuery(
        buildPrompt(projectRule.project){
          userMessage {
            text("""
            I'm getting the following error while syncing my project. The error is: !!some error message!!
            ```
            Detailed error message
            ```
            How do I fix this?
        """.trimIndent(), emptyList())
          }
        },
        StudioBot.RequestSource.BUILD)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sends query to ChatService without gradle command`() {
    // Given: Context is allowed.
    setStudioBotInstanceAvailability(isAvailable = true, isContextAllowed = true)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      verify(StudioBot.getInstance().chat(projectRule.project)).sendChatQuery(
        buildPrompt(projectRule.project){
          userMessage {
            text("""
            I'm getting the following error while building my project. The error is: !!some error message!!
            ```
            Detailed error message
            ```
            How do I fix this?
        """.trimIndent(), emptyList())
          }
        },
        StudioBot.RequestSource.BUILD)
    }
  }


  @Test
  fun `test 'Ask Gemini' quick fix stages query to ChatService when context not allowed`() {
    // Given: Context is not allowed
    setStudioBotInstanceAvailability(isAvailable = true, isContextAllowed = false)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR)

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      verify(StudioBot.getInstance().chat(projectRule.project)).stageChatQuery(
        """
        I'm getting the following error while building my project. The error is: !!some error message!!
        ```
        Detailed error message
        ```
        How do I fix this?
        """.trimIndent(),
        StudioBot.RequestSource.BUILD)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix parses Gradle command from projectId`() {
    // Given: Context is not allowed
    setStudioBotInstanceAvailability(isAvailable = true, isContextAllowed = false)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)
    messageEvent = createMessageEvent(ERROR, id = "[-4474:2441] > [Task :app:compileDebugJavaWithJavac]")

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      verify(StudioBot.getInstance().chat(projectRule.project)).stageChatQuery(
        """
        I'm getting the following error while building my project. The error is: !!some error message!!
        ```
        ${'$'} ./gradlew :app:compileDebugJavaWithJavac
        Detailed error message
        ```
        How do I fix this?
        """.trimIndent(),
        StudioBot.RequestSource.BUILD)
    }
  }

  private fun setStudioBotInstanceAvailability(isAvailable: Boolean, isContextAllowed: Boolean = false) {
    val studioBot = object : StudioBot.StubStudioBot() {
      override fun isContextAllowed(project: Project): Boolean = isContextAllowed
      override fun isAvailable(): Boolean = isAvailable
      private val _chatService = spy(object : ChatService.StubChatService() {})
      override fun chat(project: Project): ChatService = _chatService
    }
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, studioBot, projectRule.project)
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