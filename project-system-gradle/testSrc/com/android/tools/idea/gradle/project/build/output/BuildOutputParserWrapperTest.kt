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


import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
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
  val tempDirRule = TemporaryDirectoryRule()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()
  private val mockGradleErrorQuickFixProvider = mock<GradleErrorQuickFixProvider>()
  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent

  @Before
  fun setup() {
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser, ID)
    whenever(ID.type).thenReturn(ExternalSystemTaskType.REFRESH_TASKS_LIST)

    ApplicationManager.getApplication().registerExtension(GradleErrorQuickFixProvider.EP_NAME, mockGradleErrorQuickFixProvider, projectRule.project)
  }


  @Test
  fun `test when GradleErrorQuickFixProvider is not available, quick fix is not added to the build event`() {
    messageEvent = createMessageEvent(ERROR)
    myParserWrapper.parse(null, null) { event ->
      // MessageEvent is not converted into a BuildIssueEvent which holds the quickfix.
      assertThat(event).isSameAs(messageEvent)
    }
  }

  @Test
  fun `test when GradleErrorQuickFixProvider is available, a quick fix is added to the build event`() {
    messageEvent = createMessageEvent(ERROR)

    whenever(mockGradleErrorQuickFixProvider.createBuildIssueAdditionalQuickFix(messageEvent, ID)).thenReturn(
      object: DescribedBuildIssueQuickFix{
        override val description: String
          get() = "Quick fix description"
        override val id: String
          get() = "com.some.plugin.quickfix"
      }
    )

    myParserWrapper.parse(null, null) { event ->
      // MessageEvent is converted into a BuildIssueEvent which holds the quickfix.
      assertThat(event).isInstanceOf(BuildIssueEvent::class.java)
      assertThat(event.description).isEqualTo("""
        ${messageEvent.description}
        <a href="com.some.plugin.quickfix">Quick fix description</a>""".trimIndent())
      val quickFixes = (event as BuildIssueEvent).issue.quickFixes
      assertThat(quickFixes).hasSize(1)
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

  companion object {
    val ID = mock<ExternalSystemTaskId>()
  }
}