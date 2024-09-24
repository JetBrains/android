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

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.disposable
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
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class BuildOutputParserWrapperTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  private lateinit var myProject: Project

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent
  private lateinit var outputParserManager: BuildOutputParserManager

  @Before
  fun setUp() {
    // Set StudioBot's availability to true by default.
    setStudioBotInstanceAvailability(true)
    MockitoAnnotations.initMocks(this)
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser)
    outputParserManager = BuildOutputParserManager(myProject, listOf(myParserWrapper))

    whenever(myProject.basePath).thenReturn("test")

    val moduleManager = Mockito.mock(ModuleManager::class.java)
    whenever(myProject.getService(ModuleManager::class.java)).thenReturn(moduleManager)
    whenever(moduleManager.modules).thenReturn(emptyArray<Module>())
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

  private fun setStudioBotInstanceAvailability(isAvailable: Boolean) {
    val studioBot = object : StudioBot.StubStudioBot() {
      override fun isAvailable(): Boolean = isAvailable
    }
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, studioBot, projectRule.disposable)
  }

  private fun createMessageEvent(
    kind: MessageEvent.Kind,
    group: String = "Compiler",
    message: String = "!!some error message!!",
    detailedMessage: String = "Detailed error message"
  ): MessageEventImpl {
    return MessageEventImpl(
      ID,
      kind,
      group,
      message,
      detailedMessage)
  }

  private fun createFileMessageEvent(
    kind: MessageEvent.Kind,
    group: String = "Compiler",
    message: String = "!!some error message!!",
    detailedMessage: String = "Detailed error message"
  ): FileMessageEvent {
    val folder = temporaryFolder.newFolder("test")
    return FileMessageEventImpl(
      ID,
      kind,
      group,
      message,
      detailedMessage,
      FilePosition(FileUtils.join(folder, "main", "src", "main.java"),1, 1))
  }

  companion object {
    val ID = MockitoKt.mock<ExternalSystemTaskId>()
  }
}