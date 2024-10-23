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
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.insights.ui.FakeGeminiPluginApi
import com.android.tools.idea.insights.ui.INSIGHT_KEY
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import javax.swing.JButton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InsightBottomPanelTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(EdtRule()).around(projectRule).around(controllerRule)

  private lateinit var copyProvider: FakeCopyProvider
  private lateinit var testEvent: AnActionEvent
  private lateinit var experimentFetcher: FakeExperimentFetcher
  private lateinit var fakeUi: FakeUi
  private val scope = CoroutineScope(EmptyCoroutineContext)
  private val currentInsightFlow =
    MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setup() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.contextAllowed = false
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
    copyProvider = FakeCopyProvider()
    currentInsightFlow.update { LoadingState.Ready(null) }
    testEvent =
      TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.COPY_PROVIDER.`is`(it) -> copyProvider
          CommonDataKeys.PROJECT.`is`(it) -> projectRule.project
          INSIGHT_KEY.`is`(it) -> currentInsightFlow.value
          else -> null
        }
      }
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
  fun `test copy action`() = runBlocking {
    val bottomPanel = createInsightBottomPanel()

    val fakeUi = FakeUi(bottomPanel)
    val toolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place == "InsightBottomPanelRightToolBar" }
        ?: fail("Toolbar not found")
    assertThat(toolbar.actions.size).isEqualTo(2)
    val copyAction = toolbar.actions[0]

    CopyPasteManager.copyTextToClipboard("default text")

    copyAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabled).isFalse()
    assertThat(testEvent.presentation.isVisible).isTrue()

    copyProvider.text = "interesting insight"

    copyAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabled).isTrue()
    assertThat(testEvent.presentation.isVisible).isTrue()
  }

  @Test
  fun `check dialog popup content`() {
    val bottomPanel = createInsightBottomPanel()

    var message = ""
    TestDialogManager.setTestDialog {
      message = it
      Messages.OK
    }

    fakeUi.findContextButton().doClick()

    assertThat(message)
      .contains(
        "Android Studio needs to send code and context from your project " +
          "to enhance the insight for this issue."
      )
    assertThat(message).contains("Would you like to continue?")
  }

  @Test
  fun `button is invisible when context is allowed by setting`() {
    fakeGeminiPluginApi.contextAllowed = true
    createInsightBottomPanel()
    currentInsightFlow.update { LoadingState.Ready(AiInsight("")) }
    assertThat(fakeUi.findContextButton().isVisible).isFalse()
  }

  @Test
  fun `enable context button disappears and callback is triggered after user clicks ok`() {
    var onCallBack = false

    val bottomPanel = createInsightBottomPanel { onCallBack = true }
    currentInsightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }
    TestDialogManager.setTestDialog { Messages.OK }

    fakeUi.findContextButton().doClick()
    assertThat(fakeUi.findContextButton().isVisible).isFalse()

    currentInsightFlow.update { LoadingState.Ready(AiInsight("", Experiment.TOP_SOURCE)) }
    assertThat(fakeUi.findContextButton().isVisible).isFalse()

    assertThat(onCallBack).isTrue()
  }

  @Test
  fun `enable context button is still visible after user clicks cancel`() = runTest {
    createInsightBottomPanel()
    TestDialogManager.setTestDialog { Messages.CANCEL }
    currentInsightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

    fakeUi.findContextButton().doClick()
    assertThat(fakeUi.findContextButton().isVisible).isTrue()
  }

  @Test
  fun `enable context button is only visible when user is assigned an experiment`() = runTest {
    createInsightBottomPanel()
    currentInsightFlow.update { LoadingState.Ready(AiInsight("", Experiment.UNKNOWN)) }

    for (experiment in
      listOf(Experiment.ALL_SOURCES, Experiment.TOP_THREE_SOURCES, Experiment.TOP_SOURCE)) {
      experimentFetcher.experiment = experiment
      currentInsightFlow.update { LoadingState.Ready(AiInsight("")) }
      delayUntilCondition(200) { fakeUi.findContextButton().isVisible }
      // Reset the button visibility for the next experiment
      currentInsightFlow.update { LoadingState.Ready(AiInsight("", experiment)) }
      delayUntilCondition(200) { !fakeUi.findContextButton().isVisible }
    }

    for (experiment in listOf(Experiment.CONTROL, Experiment.UNKNOWN)) {
      experimentFetcher.experiment = experiment
      currentInsightFlow.update { LoadingState.Ready(AiInsight("")) }
      delayUntilCondition(200) { !fakeUi.findContextButton().isVisible }
      // Reset the button visibility for the next experiment
      experimentFetcher.experiment = Experiment.TOP_SOURCE
      // isCached = true is only for the flow to update.
      // In the case of UNKNOWN, the experiment remains the same and the flow does not update
      currentInsightFlow.update { LoadingState.Ready(AiInsight("", experiment, true)) }
      delayUntilCondition(200) { fakeUi.findContextButton().isVisible }
    }
  }

  private class FakeCopyProvider(var text: String = "") : CopyProvider {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun performCopy(dataContext: DataContext) = Unit

    override fun isCopyEnabled(dataContext: DataContext) = text.isNotBlank()

    override fun isCopyVisible(dataContext: DataContext) = true
  }

  private class FakeExperimentFetcher(var experiment: Experiment) : AppInsightsExperimentFetcher {
    override fun getCurrentExperiment(experimentGroup: ExperimentGroup) = experiment
  }

  private fun createInsightBottomPanel(callback: (Boolean) -> Unit = {}) =
    InsightBottomPanel(controllerRule.controller, scope, currentInsightFlow, callback).also {
      fakeUi = FakeUi(it)
    }

  private fun FakeUi.findContextButton() =
    findComponent<JButton>() ?: fail("Context button not found")
}
