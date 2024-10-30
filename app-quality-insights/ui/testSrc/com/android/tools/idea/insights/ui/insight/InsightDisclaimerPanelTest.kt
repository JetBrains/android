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
import com.android.tools.adtui.stdui.CommonHyperLinkLabel
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.StubAppInsightsProjectLevelController
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.FakeAiInsightToolkit
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.Language
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.ui.FakeGeminiPluginApi
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.time.Instant
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class InsightDisclaimerPanelTest {

  private lateinit var scope: CoroutineScope
  private val insightFlow = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = ProjectRule()

  private val codeContextResolver =
    FakeCodeContextResolver(
      listOf(
        CodeContext("Class", "/a/b/c", "fileContent", Language.KOTLIN),
        CodeContext("Class2", "/a/b/c2", "fileContent2", Language.KOTLIN),
        CodeContext("Class3", "/a/b/c3", "fileContent3", Language.KOTLIN),
      )
    )
  private val stateFlow =
    MutableStateFlow(
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
      )
    )
  private val stubController =
    StubAppInsightsProjectLevelController(
      state = stateFlow,
      aiInsightToolkit = FakeAiInsightToolkit(codeContextResolver),
    )
  private lateinit var fakeUi: FakeUi
  private lateinit var experimentFetcher: FakeExperimentFetcher
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
    experimentFetcher = FakeExperimentFetcher(Experiment.ALL_SOURCES)
    application.replaceService(
      AppInsightsExperimentFetcher::class.java,
      experimentFetcher,
      projectRule.disposable,
    )
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `test disclaimer text`() = runBlocking {
    insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.ALL_SOURCES)) }

    val disclaimerPanel = createDisclaimerPanel()
    val fakeUi = FakeUi(disclaimerPanel)

    waitForCondition(2.seconds) {
      fakeUi.findVisibleLabel()?.strippedHtmlText() ==
        "This insight was generated with code context."
    }

    insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

    waitForCondition(2.seconds) {
      fakeUi.findVisibleLabel()?.strippedHtmlText() ==
        "This insight was generated without code context. For better results, review and share limited code context with Gemini."
    }
  }

  @Test
  fun `check dialog popup content`() {
    createDisclaimerPanel()

    var message = ""
    TestDialogManager.setTestDialog {
      message = it
      Messages.CANCEL
    }

    clickOnLink()
    waitForCondition(2.seconds) { message.isNotEmpty() }

    val expectedMessage =
      "<html>Android Studio needs to send code context from your project to enhance the insight for this issue." +
        "<br>Would you like to continue?<br><b>Files to be shared</b>: c, c2, c3\n" +
        "<p style=\"color:#999999;margin-top:5px;\">" +
        "You can change your context sharing preference from Settings > Tools > Gemini.</p></html>"

    assertThat(message).isEqualTo(expectedMessage)

    // Assert no context popup
    setCodeContextResolver(FakeCodeContextResolver(emptyList()))
    message = ""
    clickOnLink()
    waitForCondition(2.seconds) { message.isNotEmpty() }
    assertThat(message)
      .isEqualTo(
        "Android Studio attempted to find relevant files to improve this insight but found none. Please try again at a later time."
      )

    // For CONTROL, it should show the message with files when the context is available
    setCodeContextResolver(codeContextResolver)
    experimentFetcher.experiment = Experiment.CONTROL
    message = ""
    clickOnLink()
    waitForCondition(2.seconds) { message.isNotEmpty() }

    assertThat(message).isEqualTo(expectedMessage)
  }

  @Test
  fun `show default context disclaimer when sharing is allowed for all projects`() = runBlocking {
    fakeGeminiPluginApi.contextAllowed = true
    createDisclaimerPanel()
    insightFlow.update {
      LoadingState.Ready(AiInsight("", experiment = experimentFetcher.experiment))
    }
    waitForCondition(2.seconds) { !isPromptingContextDisclaimer() }
  }

  @Test
  fun `context sharing prompt is removed from disclaimer and callback is triggered after user clicks ok`() =
    runBlocking {
      var onCallBack = false

      createDisclaimerPanel { onCallBack = true }
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }
      TestDialogManager.setTestDialog { Messages.OK }
      waitForCondition(2.seconds) { isPromptingContextDisclaimer() }

      clickOnLink()
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.TOP_SOURCE)) }
      waitForCondition(2.seconds) { !isPromptingContextDisclaimer() }

      waitForCondition(2.seconds) { onCallBack }
    }

  @Test
  fun `context sharing prompt is still visible after user clicks cancel`() = runBlocking {
    createDisclaimerPanel()
    TestDialogManager.setTestDialog { Messages.CANCEL }
    insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

    clickOnLink()
    assertThat(isPromptingContextDisclaimer()).isTrue()
  }

  @Test
  fun `enable context prompt disclaimer is shown when context sharing setting is off and current insight's experiment is unknown`() =
    runBlocking {
      createDisclaimerPanel()
      insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

      for (experiment in
        listOf(
          Experiment.CONTROL,
          Experiment.ALL_SOURCES,
          Experiment.TOP_THREE_SOURCES,
          Experiment.TOP_SOURCE,
        )) {
        experimentFetcher.experiment = experiment
        insightFlow.update { LoadingState.Ready(AiInsight("")) }
        waitForCondition(2.seconds) { isPromptingContextDisclaimer() }
        // Reset the button visibility for the next experiment
        insightFlow.update { LoadingState.Ready(AiInsight("", experiment)) }
        waitForCondition(2.seconds) { !isPromptingContextDisclaimer() }
      }
    }

  private fun FakeUi.findVisibleLabel() = findComponent<JLabel> { it.isVisible }

  private fun JLabel.strippedHtmlText() = text.replace("<html>", "").replace("</html>", "")

  private fun createDisclaimerPanel(onRefreshInsight: (Boolean) -> Unit = {}) =
    InsightDisclaimerPanel(stubController, scope, insightFlow, onRefreshInsight).also {
      fakeUi = FakeUi(it)
    }

  private fun clickOnLink() = fakeUi.findHyperLinkLabel().hyperLinkListeners.forEach { it() }

  private fun FakeUi.findHyperLinkLabel() = findComponent<CommonHyperLinkLabel>()!!

  private fun isPromptingContextDisclaimer() =
    fakeUi.findComponent<JPanel> { it.name == "without_code_disclaimer_panel" }!!.isVisible

  private fun setCodeContextResolver(codeContextResolver: CodeContextResolver) {
    (stubController.aiInsightToolkit as FakeAiInsightToolkit).codeContextResolver =
      codeContextResolver
  }
}
