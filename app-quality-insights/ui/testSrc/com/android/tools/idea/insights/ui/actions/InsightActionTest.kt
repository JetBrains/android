/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.actions

import com.android.testutils.waitForCondition
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventData
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.ModelType
import com.android.tools.idea.studiobot.StubModel
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InsightActionTest {

  private val event =
    Event(
      name = "event",
      eventData = EventData(Device("manufacturer", "model"), OperatingSystemInfo("14", "")),
      stacktraceGroup = createStackTraceGroup(),
    )
  private val issue =
    AppInsightsIssue(
      IssueDetails(
        IssueId("1234"),
        "Issue1",
        "com.google.crash.Crash",
        FailureType.FATAL,
        "Sample Event",
        "1.2.3",
        "1.2.3",
        8L,
        14L,
        5L,
        50L,
        emptySet(),
        "https://url.for-crash.com",
        0,
        annotations = emptyList(),
      ),
      event,
    )

  private val scope = CoroutineScope(EmptyCoroutineContext)
  private val fakeOnboardingFlow = MutableStateFlow(false)
  private var isGeminiToolWindowOpen = false

  private val fakeChatService =
    object : ChatService {
      var stagedPrompt = ""

      override fun sendChatQuery(
        prompt: Prompt,
        requestSource: StudioBot.RequestSource,
        displayText: String?,
      ) = Unit

      override fun stageChatQuery(prompt: String, requestSource: StudioBot.RequestSource) {
        isGeminiToolWindowOpen = true
        stagedPrompt = prompt
      }
    }

  private val fakeStudioBot =
    object : StudioBot {
      override val MAX_QUERY_CHARS = 1000

      override fun isAvailable() = fakeOnboardingFlow.value

      override fun aiExcludeService() = AiExcludeService.FakeAiExcludeService()

      override fun chat(project: Project) = fakeChatService

      override fun model(project: Project, modelType: ModelType) = StubModel()
    }

  @get:Rule val projectRule = ProjectRule()

  @Before
  fun setup() {
    application.replaceService(StudioBot::class.java, fakeStudioBot, projectRule.disposable)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `insight action opens Gemini toolwindow if not open`() {
    val insightAction = createInsightButton()
    assertThat(insightAction.text).isEqualTo("Enable insights")
    assertThat(insightAction.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")
    insightAction.doClick()
    assertThat(isGeminiToolWindowOpen).isTrue()
  }

  @Test
  fun `insight action changes text when onboarding state changes`() {
    val insightAction = createInsightButton()
    assertThat(insightAction.text).isEqualTo("Enable insights")
    assertThat(insightAction.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")

    fakeOnboardingFlow.update { true }
    insightAction.update()
    waitForCondition(2.seconds) { insightAction.text != "Enable insights" }

    assertThat(insightAction.text).isEqualTo("Show insights")
    assertThat(insightAction.toolTipText).isEqualTo("Show insights for this issue")

    fakeOnboardingFlow.update { false }
    insightAction.update()
    waitForCondition(2.seconds) { insightAction.text != "Show insights" }

    assertThat(insightAction.text).isEqualTo("Enable insights")
    assertThat(insightAction.toolTipText).isEqualTo("Complete Gemini onboarding to enable insights")
  }

  @Test
  fun `insight action stages prompt when studio bot available`() {
    val insightAction = createInsightButton()
    fakeOnboardingFlow.update { true }
    insightAction.update()
    waitForCondition(2.seconds) { insightAction.text != "Enable insights" }

    assertThat(insightAction.text).isEqualTo("Show insights")
    assertThat(insightAction.toolTipText).isEqualTo("Show insights for this issue")

    insightAction.doClick()

    val expectedPrompt =
      "Explain this exception from my app running on manufacturer model with Android version 14:\n" +
        "Exception:\n" +
        "```\n" +
        "rawExceptionMessage: Some Exception Message - 1\n" +
        "\tframe-0\n" +
        "\tframe-1\n" +
        "\tframe-2\n" +
        "\tframe-3\n" +
        "\tframe-4\n" +
        "Caused by rawExceptionMessage: Some Exception Message - 2\n" +
        "\tframe-0\n" +
        "\tframe-1\n" +
        "\tframe-2\n" +
        "\tframe-3\n" +
        "\tframe-4\n" +
        "```"
    assertThat(fakeChatService.stagedPrompt).isEqualTo(expectedPrompt)
  }

  private fun createInsightButton() =
    InsightAction(StudioBot.RequestSource.CRASHLYTICS, projectRule::project) { issue }

  private fun createStackTraceGroup() = StacktraceGroup(List(5) { createRandomException(it) })

  private fun createRandomException(idx: Int): ExceptionStack {
    return ExceptionStack(
      createStackTrace(),
      rawExceptionMessage =
        "${if(idx == 1) "Caused by " else ""}rawExceptionMessage: Some Exception Message - ${idx+1}",
    )
  }

  private fun createStackTrace() = Stacktrace(frames = List(5) { Frame(rawSymbol = "frame-$it") })

  private val InsightAction.text: String
    get() = (component as JButton).text

  private val InsightAction.toolTipText: String
    get() = (component as JButton).toolTipText

  private fun InsightAction.doClick() = (component as JButton).doClick()

  private fun InsightAction.update() = updateCustomComponent(component, templatePresentation)
}
