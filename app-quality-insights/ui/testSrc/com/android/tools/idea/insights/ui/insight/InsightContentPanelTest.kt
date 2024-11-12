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
package com.android.tools.idea.insights.ui.insight

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.com.google.rpc.Status
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.android.tools.idea.protobuf.Any
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.application
import com.intellij.util.ui.StatusText
import java.net.SocketTimeoutException
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
class InsightContentPanelTest {
  private val projectRule = ProjectRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(EdtRule()).around(projectRule)

  private lateinit var currentInsightFlow: MutableStateFlow<LoadingState<AiInsight?>>
  private lateinit var insightContentPanel: InsightContentPanel

  private val errorText: String
    get() = insightContentPanel.emptyStateText.text

  private val secondaryText: String
    get() = insightContentPanel.emptyStateText.secondaryComponent.toString()

  private val enableInsightDeferred = CompletableDeferred<Boolean>(null)

  private val mockController = mock<AppInsightsProjectLevelController>()

  private var isStudioBotAvailable = false
  private val stubStudioBot =
    object : StudioBot.StubStudioBot() {
      override fun isAvailable() = isStudioBotAvailable
    }

  private val scope = CoroutineScope(EmptyCoroutineContext)

  @Before
  fun setup() = runBlocking {
    doReturn(projectRule.project).whenever(mockController).project
    application.replaceService(StudioBot::class.java, stubStudioBot, projectRule.disposable)
    currentInsightFlow = MutableStateFlow(LoadingState.Ready(AiInsight("insight")))
    insightContentPanel =
      InsightContentPanel(
        mockController,
        scope,
        currentInsightFlow,
        projectRule.disposable,
        object : InsightPermissionDeniedHandler {
          override fun handlePermissionDenied(
            permissionDenied: LoadingState.PermissionDenied,
            statusText: StatusText,
          ) {
            statusText.apply {
              clear()
              appendText("handling permission denied")
              appendLine("simple redirecting message")
            }
          }
        },
        { enableInsightDeferred.complete(true) },
      ) {}
  }

  @After
  fun teardown() {
    scope.cancel()
  }

  @Test
  fun `test loading state`() = runBlocking {
    currentInsightFlow.update { LoadingState.Loading }

    val fakeUi = FakeUi(insightContentPanel)

    val loadingPanel = fakeUi.findComponent<JBLoadingPanel>() ?: fail("Loading panel not found")
    assertThat(loadingPanel.getLoadingText()).isEqualTo("Generating insight...")
  }

  @Test
  fun `test permission denied`() = runBlocking {
    currentInsightFlow.update { LoadingState.PermissionDenied("Some complex message") }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("handling permission denied")
    assertThat(secondaryText).isEqualTo("simple redirecting message")
  }

  @Test
  fun `test value null, then action not fired text`() = runBlocking {
    currentInsightFlow.update { LoadingState.Ready(null) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText)
      .isEqualTo(
        "Transient state (\"fetch insight\" action is not fired yet), should recover shortly."
      )
  }

  @Test
  fun `test insight empty, then no insight`() = runBlocking {
    currentInsightFlow.update { LoadingState.Ready(DEFAULT_AI_INSIGHT) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("No insights")
    assertThat(secondaryText).isEqualTo("There are no insights available for this issue")
  }

  @Test
  fun `test failure shows failure message if available`() = runBlocking {
    currentInsightFlow.update {
      LoadingState.ServerFailure("Some failure", SocketTimeoutException())
    }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Request failed")
    assertThat(secondaryText).isEqualTo("Some failure")
  }

  @Test
  fun `test failure shows generic failure message when message unavailable`() = runBlocking {
    currentInsightFlow.update { LoadingState.ServerFailure(null) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Request failed")
    assertThat(secondaryText).isEqualTo("An unknown failure occurred")
  }

  @Test
  fun `test tos not accepted shows enable insight button`() = runBlocking {
    currentInsightFlow.update { LoadingState.TosNotAccepted }

    val fakeUi = FakeUi(insightContentPanel)

    val statusTexts =
      fakeUi
        .findAllComponents<kotlin.Any> { it.javaClass.name.contains("StatusText\$Fragment") }
        .map { it.toString() }

    assertThat(statusTexts.size).isEqualTo(2)
    assertThat(statusTexts[0]).isEqualTo("Insights require Gemini")
    assertThat(statusTexts[1])
      .isEqualTo("You can setup Gemini and enable insights via button below")

    val button = fakeUi.findComponent<JButton>() ?: fail("Button not found")
    assertThat(button.text).isEqualTo("Enable Insights")
    assertThat(button.isVisible).isTrue()

    button.doClick()
    assertThat(enableInsightDeferred.await()).isTrue()
  }

  @Test
  fun `test offline mode`() = runBlocking {
    currentInsightFlow.update { LoadingState.NetworkFailure(null) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Insights data is not available.")
    assertThat(secondaryText).isEmpty()
  }

  @Test
  fun `test quota exhausted`() = runBlocking {
    currentInsightFlow.update {
      LoadingState.NetworkFailure(
        "Quota exceeded for quota metric 'Duet Task API requests' and limit 'Duet Task API requests per day per user' of service 'cloudaicompanion.googleapis.com' for consumer 'project_number:123456789'."
      )
    }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Quota exhausted")
    assertThat(secondaryText)
      .isEqualTo("You have consumed your available daily quota for insights.")
  }

  @Test
  fun `test gemini is not enabled`() = runBlocking {
    currentInsightFlow.update { LoadingState.Unauthorized("Gemini is disabled") }

    val fakeUi = FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Gemini is disabled")
    assertThat(secondaryText)
      .isEqualTo("To see insights, please enable and authorize the Gemini plugin")

    val toolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place == "GeminiOnboardingObserver" }
        ?: fail("Toolbar not found")
    val action =
      toolbar.actionGroup.getChildren(null).firstOrNull() ?: fail("Observer action not found")

    isStudioBotAvailable = true
    action.update(createTestEvent())

    verify(mockController).refreshInsight(false)
  }

  @Test
  fun `test unsupported operation`() = runBlocking {
    currentInsightFlow.update { LoadingState.UnsupportedOperation("Some message") }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("No insight available")
    assertThat(secondaryText).isEqualTo("Some message")
  }

  @Test
  fun `test temporary kill switch message`() = runBlocking {
    val status =
      Status.newBuilder()
        .apply {
          val message =
            "SomeException: Cannot process request for disabled experience at\nsome stacktrace"
          val any = Any.newBuilder().setValue(ByteString.copyFrom(message.toByteArray()))
          addDetails(any)
        }
        .build()
    currentInsightFlow.update { LoadingState.UnknownFailure("Some message", null, status) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Request failed")
    assertThat(secondaryText)
      .isEqualTo("Insights feature is temporarily unavailable, check back later.")
  }

  private suspend fun delayUntilStatusTextVisible() =
    delayUntilCondition(200) { insightContentPanel.emptyStateText.isStatusVisible }
}
