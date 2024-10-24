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
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.FakeGeminiToolkit
import com.android.tools.idea.insights.ai.StubInsightsOnboardingProvider
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.ui.FakeGeminiPluginApi
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.android.tools.idea.protobuf.Any
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginFeatureRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.StatusText
import java.net.SocketTimeoutException
import java.time.Instant
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class InsightContentPanelTest {
  private val projectRule = ProjectRule()
  private val loginFeatureRule = LoginFeatureRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(EdtRule()).around(projectRule).around(loginFeatureRule)

  private lateinit var currentInsightFlow: MutableStateFlow<LoadingState<AiInsight?>>
  private lateinit var insightContentPanel: InsightContentPanel

  private val errorText: String
    get() = insightContentPanel.emptyStateText.text

  private val secondaryText: String
    get() = insightContentPanel.emptyStateText.secondaryComponent.toString()

  private val enableInsightDeferred = CompletableDeferred<Boolean>(null)

  private val mockController = mock<AppInsightsProjectLevelController>()

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi
  private val scope = CoroutineScope(EmptyCoroutineContext)
  private val onboardingProvider =
    object : StubInsightsOnboardingProvider() {
      override fun performOnboardingAction(connection: Connection) {
        enableInsightDeferred.complete(true)
      }
    }

  private lateinit var experimentFetcher: FakeExperimentFetcher

  @Before
  fun setup() = runBlocking {
    doReturn(
        flowOf(
          AppInsightsState(
            Selection(CONNECTION1, listOf(CONNECTION1)),
            TEST_FILTERS,
            LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          )
        )
      )
      .whenever(mockController)
      .state
    doReturn(projectRule.project).whenever(mockController).project
    doReturn(FakeGeminiToolkit(true, aiInsightOnboardingProvider = onboardingProvider))
      .whenever(mockController)
      .geminiToolkit
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.available = false
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
    currentInsightFlow = MutableStateFlow(LoadingState.Ready(AiInsight("insight")))
    experimentFetcher = FakeExperimentFetcher(Experiment.CONTROL)
    ApplicationManager.getApplication()
      .replaceService(
        AppInsightsExperimentFetcher::class.java,
        experimentFetcher,
        projectRule.disposable,
      )
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
  fun `test user needs onboarding shows enable insight button`() = runBlocking {
    currentInsightFlow.update { LoadingState.Unauthorized("") }

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
  fun `test gemini is not enabled due to plugin not enabled`() = runBlocking {
    currentInsightFlow.update { LoadingState.Unauthorized("Gemini is disabled") }

    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo(GEMINI_NOT_AVAILABLE)
    assertThat(secondaryText)
      .isEqualTo("To see insights, please enable the Gemini plugin in Settings > Plugins")
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

  @Test
  fun `disclaimer is not shown for users not assigned to a code context experiment`() =
    runBlocking {
      experimentFetcher.experiment = Experiment.UNKNOWN
      val insightContentPanel =
        InsightContentPanel(
          mockController,
          scope,
          currentInsightFlow,
          projectRule.disposable,
          object : InsightPermissionDeniedHandler {
            override fun handlePermissionDenied(
              permissionDenied: LoadingState.PermissionDenied,
              statusText: StatusText,
            ) {}
          },
          { enableInsightDeferred.complete(true) },
        )

      assertThat(FakeUi(insightContentPanel).findComponent<InsightDisclaimerPanel>()).isNull()
    }

  private suspend fun delayUntilStatusTextVisible() =
    delayUntilCondition(200) { insightContentPanel.emptyStateText.isStatusVisible }
}
