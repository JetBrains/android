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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.StubAppInsightsProjectLevelController
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.ui.FakeGeminiPluginApi
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.net.URL
import java.time.Instant
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
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
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunsInEdt
class InsightDisclaimerPanelTest {

  private lateinit var scope: CoroutineScope
  private val insightFlow = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))
  private val conn = mock<Connection>().apply { doReturn(true).whenever(this).isMatchingProject() }
  private val state =
    AppInsightsState(
      Selection(conn, listOf(conn)),
      TEST_FILTERS,
      LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
    )

  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = ProjectRule()

  private lateinit var fakeUi: FakeUi
  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setup() {
    scope = CoroutineScope(EmptyCoroutineContext)
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.contextAllowed = false
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
    application.replaceService(ShowSettingsUtil::class.java, mock(), projectRule.disposable)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `context sharing disclaimer is removed and callback is triggered after user shares context`() =
    runBlocking {
      val refreshInsightCalled = CompletableDeferred<Boolean>()
      val disclaimerPanel =
        createDisclaimerPanel(
          controller =
            object : StubAppInsightsProjectLevelController(state = MutableStateFlow(state)) {
              override fun refreshInsight(regenerateWithContext: Boolean) {
                refreshInsightCalled.complete(regenerateWithContext)
              }
            }
        )
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }
      waitForCondition(2.seconds) { disclaimerPanel.isVisible }

      clickOnLink()
      fakeGeminiPluginApi.contextAllowed = true
      fakeUi.updateToolbars()
      assertThat(refreshInsightCalled.await()).isTrue()

      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.TOP_SOURCE)) }
      waitForCondition(2.seconds) { !disclaimerPanel.isVisible }
    }

  @Test
  fun `enable context prompt disclaimer is shown when context sharing setting is off and current insight's experiment is unknown`() =
    runBlocking {
      val disclaimerPanel =
        createDisclaimerPanel(
          StubAppInsightsProjectLevelController(state = MutableStateFlow(state))
        )
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

      for (experiment in
        listOf(
          Experiment.CONTROL,
          Experiment.ALL_SOURCES,
          Experiment.TOP_THREE_SOURCES,
          Experiment.TOP_SOURCE,
        )) {
        insightFlow.update { LoadingState.Ready(AiInsight("", experiment = Experiment.UNKNOWN)) }
        waitForCondition(2.seconds) { disclaimerPanel.isVisible }
        // Reset the button visibility for the next experiment
        insightFlow.update { LoadingState.Ready(AiInsight("", experiment = experiment)) }
        waitForCondition(2.seconds) { !disclaimerPanel.isVisible }
      }
    }

  @Test
  fun `project mismatch panel shown when context enabled and project different from connection`() =
    runBlocking {
      doReturn(false).whenever(conn).isMatchingProject()
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.TOP_SOURCE)) }
      createDisclaimerPanel(StubAppInsightsProjectLevelController(state = MutableStateFlow(state)))

      val textPane = fakeUi.findComponent<JTextPane> { it.isVisible } ?: fail("JTextPane not found")
      // TextPane text contains html tags. Clean up the spacing in order to match the expected text
      val text = textPane.text.split("\n").joinToString(" ") { it.trim() }
      assertThat(text)
        .contains(
          "This insight was generated without code context because the currently open project does not appear to match the project selected in Firebase Crashlytics"
        )
    }

  private fun createDisclaimerPanel(
    controller: AppInsightsProjectLevelController =
      StubAppInsightsProjectLevelController(state = MutableStateFlow(state))
  ) = InsightDisclaimerPanel(controller, scope, insightFlow).also { fakeUi = FakeUi(it) }

  private fun clickOnLink() =
    fakeUi.findHyperLinkLabel().hyperlinkListeners.forEach {
      it.hyperlinkUpdate(
        HyperlinkEvent(
          fakeUi.findHyperLinkLabel(),
          HyperlinkEvent.EventType.ACTIVATED,
          URL("https://www.google.com"),
        )
      )
    }

  private fun FakeUi.findHyperLinkLabel() = findComponent<JEditorPane>()!!
}
