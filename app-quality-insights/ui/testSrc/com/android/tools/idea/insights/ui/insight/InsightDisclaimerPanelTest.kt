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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.StubAppInsightsProjectLevelController
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
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import kotlin.coroutines.EmptyCoroutineContext
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

@RunsInEdt
class InsightDisclaimerPanelTest {

  private lateinit var scope: CoroutineScope
  private val insightFlow = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

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
            object : StubAppInsightsProjectLevelController() {
              override fun refreshInsight(contextSharingOverride: Boolean) {
                refreshInsightCalled.complete(contextSharingOverride)
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
      val disclaimerPanel = createDisclaimerPanel()
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

  private fun createDisclaimerPanel(
    controller: AppInsightsProjectLevelController = StubAppInsightsProjectLevelController()
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
