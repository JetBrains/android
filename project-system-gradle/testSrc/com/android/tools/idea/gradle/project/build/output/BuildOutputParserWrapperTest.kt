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


import com.android.tools.idea.gradle.project.build.events.studiobot.GradleErrorContext
import com.android.tools.idea.gradle.project.build.events.studiobot.StudioBotQuickFixProvider
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
import com.intellij.testFramework.registerExtension
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class BuildOutputParserWrapperTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()
  private val fakeStudioBotQuickFixProvider = FakeStudioBotQuickFixProvider()
  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent

  private class FakeStudioBotQuickFixProvider: StudioBotQuickFixProvider {
    var sentContext: GradleErrorContext? = null
    var sentProject: Project? = null
    var available = true
    override fun askGemini(context: GradleErrorContext, project: Project) {
      sentContext  = context
      sentProject = project
    }
    override fun isAvailable(): Boolean {
      return available
    }
  }

  @Before
  fun setup() {
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser, ID)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)
    ApplicationManager.getApplication().registerExtension(StudioBotQuickFixProvider.EP_NAME, fakeStudioBotQuickFixProvider, projectRule.project)
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
  fun `test when StudioBotQuickFixProvider is not available, 'Ask Gemini' link is not added for ERROR MessageEvent`() {
    fakeStudioBotQuickFixProvider.available = false

    messageEvent = createMessageEvent(ERROR)
    myParserWrapper.parse(null, null) { event ->

      // MessageEvent is not converted into a BuildIssueEvent which holds the link.
      assertThat(event).isNotInstanceOf(BuildIssueEvent::class.java)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sends context without gradle command when not available`() {
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)
    messageEvent = createMessageEvent(ERROR, "", "", "")

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected = """
            GradleErrorContext:
        """.trimIndent()
      assertThat(fakeStudioBotQuickFixProvider.sentContext!!.formatForTests()).isEqualTo(expected)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix parses Gradle command from projectId`() {
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)
    messageEvent = createMessageEvent(ERROR, id = "[-4474:2441] > [Task :app:compileDebugJavaWithJavac]", group = "", message = "", detailedMessage = "")

    myParserWrapper.parse(null, null) { event ->

      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected =
        """
        GradleErrorContext:
        gradleTask: :app:compileDebugJavaWithJavac
        """.trimIndent()
      assertThat(fakeStudioBotQuickFixProvider.sentContext!!.formatForTests()).isEqualTo(expected)
    }
  }


  @Test
  fun `test 'Ask Gemini' quick fix sets RequestSource as BUILD for EXECUTE_TASK`() {
    messageEvent = createMessageEvent(ERROR, "", "", "")

    whenever(ID.type).thenReturn(ExternalSystemTaskType.EXECUTE_TASK)

    myParserWrapper.parse(null, null) { event ->
      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected =
        """
        GradleErrorContext:
        source: build
        """.trimIndent()
      assertThat(fakeStudioBotQuickFixProvider.sentContext!!.formatForTests()).isEqualTo(expected)
    }
  }

  @Test
  fun `test 'Ask Gemini' quick fix sets RequestSource as SYNC for RESOLVE_PROJECT`() {
    messageEvent = createMessageEvent(ERROR, "", "", "")

    whenever(ID.type).thenReturn(ExternalSystemTaskType.RESOLVE_PROJECT)

    myParserWrapper.parse(null, null) { event ->
      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes.first()).isInstanceOf(OpenStudioBotBuildIssueQuickFix::class.java)
      quickFixes.first().runQuickFix(projectRule.project) { }

      val expected =
        """
        GradleErrorContext:
        source: sync
        """.trimIndent()
      assertThat(fakeStudioBotQuickFixProvider.sentContext!!.formatForTests()).isEqualTo(expected)
    }
  }

  private fun GradleErrorContext.formatForTests() = buildString {
    append("GradleErrorContext:")
    if (!gradleTask.isNullOrEmpty()) {
      append("\ngradleTask: $gradleTask")
    }
    if (!errorMessage.isNullOrEmpty()) {
      append("\nerrorMessage: $errorMessage")
    }
    if (!fullErrorDetails.isNullOrEmpty()) {
      append("\nfullErrorDetails: $fullErrorDetails\n")
    }
    if (source != null) {
      append("\nsource: $source")
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