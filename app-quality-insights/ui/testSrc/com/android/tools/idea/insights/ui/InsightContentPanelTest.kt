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
package com.android.tools.idea.insights.ui

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.AiInsight
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.LoadingState
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import java.net.SocketTimeoutException
import javax.swing.JButton
import kotlin.test.fail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InsightContentPanelTest {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  private val testRootDisposable
    get() = controllerRule.disposable

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(EdtRule()).around(projectRule).around(controllerRule)

  private lateinit var currentInsightFlow: MutableStateFlow<LoadingState<AiInsight?>>
  private lateinit var insightContentPanel: InsightContentPanel

  private val errorText: String
    get() = insightContentPanel.emptyStateText.text

  private val secondaryText: String
    get() = insightContentPanel.emptyStateText.secondaryComponent.toString()

  @Before
  fun setup() = runBlocking {
    currentInsightFlow = MutableStateFlow(LoadingState.Ready(AiInsight("insight")))
    insightContentPanel =
      InsightContentPanel(
        controllerRule.controller.coroutineScope,
        currentInsightFlow.asStateFlow(),
        testRootDisposable,
      )
  }

  @Test
  fun `test permission denied`() = runBlocking {
    currentInsightFlow.update { LoadingState.PermissionDenied("Some complex message") }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Request failed")

    assertThat(secondaryText).isEqualTo("You do not have permission to fetch insights")
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
    currentInsightFlow.update { LoadingState.ToSNotAccepted }

    val fakeUi = FakeUi(insightContentPanel)

    val statusTexts =
      fakeUi
        .findAllComponents<Any> { it.javaClass.name.contains("StatusText\$Fragment") }
        .map { it.toString() }
    assertThat(statusTexts.size).isEqualTo(2)
    assertThat(statusTexts[0]).isEqualTo("Insights require Gemini")
    assertThat(statusTexts[1])
      .isEqualTo("You can setup Gemini and enable insights via button below")

    val button = fakeUi.findComponent<JButton>() ?: fail("Button not found")
    assertThat(button.text).isEqualTo("Enable Insights")
    assertThat(button.isVisible).isTrue()
  }

  @Test
  fun `test offline mode`() = runBlocking {
    currentInsightFlow.update { LoadingState.NetworkFailure(null) }

    FakeUi(insightContentPanel)
    delayUntilStatusTextVisible()

    assertThat(errorText).isEqualTo("Insights data is not available.")
    assertThat(secondaryText).isEmpty()
  }

  private suspend fun delayUntilStatusTextVisible() =
    delayUntilCondition(200) { insightContentPanel.emptyStateText.isStatusVisible }
}
