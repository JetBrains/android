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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.insights.ui.INSIGHT_KEY
import com.android.tools.idea.studiobot.StudioBot
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InsightBottomPanelTest {

  private val projectRule = ProjectRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(EdtRule()).around(projectRule)

  private lateinit var studioBot: FakeStudioBot
  private lateinit var copyProvider: FakeCopyProvider
  private lateinit var testEvent: AnActionEvent
  private lateinit var experimentFetcher: FakeExperimentFetcher
  private var currentInsight: AiInsight? = null

  @Before
  fun setup() {
    studioBot = FakeStudioBot(false)
    application.replaceService(StudioBot::class.java, studioBot, projectRule.disposable)
    copyProvider = FakeCopyProvider()
    currentInsight = null
    testEvent =
      TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.COPY_PROVIDER.`is`(it) -> copyProvider
          CommonDataKeys.PROJECT.`is`(it) -> projectRule.project
          INSIGHT_KEY.`is`(it) -> currentInsight
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

  @Test
  fun `test copy action`() = runBlocking {
    val bottomPanel = InsightBottomPanel(projectRule.project) {}

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
    val bottomPanel = InsightBottomPanel(projectRule.project) {}

    var message = ""
    TestDialogManager.setTestDialog {
      message = it
      Messages.OK
    }

    bottomPanel.enableCodeContextAction.actionPerformed(testEvent)

    assertThat(message)
      .contains(
        "Android Studio needs to send code and context from your project " +
          "to enhance the insight for this issue."
      )
    assertThat(message).contains("Would you like to continue?")
  }

  @Test
  fun `button is invisible when context is allowed by setting`() {
    studioBot.isContextAllowed = true
    val bottomPanel = InsightBottomPanel(projectRule.project) {}
    bottomPanel.enableCodeContextAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun `enable context button disappears and callback is triggered after user clicks ok`() {
    var onCallBack = false

    val bottomPanel = InsightBottomPanel(projectRule.project) { onCallBack = true }
    currentInsight = AiInsight("", Experiment.UNKNOWN)
    TestDialogManager.setTestDialog { Messages.OK }

    bottomPanel.enableCodeContextAction.actionPerformed(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isFalse()

    currentInsight = AiInsight("", Experiment.TOP_SOURCE)
    bottomPanel.enableCodeContextAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isFalse()

    assertThat(onCallBack).isTrue()
  }

  @Test
  fun `enable context button is still visible after user clicks cancel`() {
    val bottomPanel = InsightBottomPanel(projectRule.project) {}
    TestDialogManager.setTestDialog { Messages.CANCEL }
    currentInsight = AiInsight("", Experiment.UNKNOWN)

    bottomPanel.enableCodeContextAction.actionPerformed(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isTrue()

    bottomPanel.enableCodeContextAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isTrue()
  }

  @Test
  fun `enable context button is only visible when user is assigned an experiment`() {
    val bottomPanel = InsightBottomPanel(projectRule.project) {}
    currentInsight = AiInsight("", Experiment.UNKNOWN)

    for (experiment in
      listOf(Experiment.ALL_SOURCES, Experiment.TOP_THREE_SOURCES, Experiment.TOP_SOURCE)) {
      experimentFetcher.experiment = experiment
      bottomPanel.enableCodeContextAction.update(testEvent)
      assertThat(testEvent.presentation.isEnabledAndVisible).isTrue()
    }

    for (experiment in listOf(Experiment.CONTROL, Experiment.UNKNOWN)) {
      experimentFetcher.experiment = experiment
      bottomPanel.enableCodeContextAction.update(testEvent)
      assertThat(testEvent.presentation.isEnabledAndVisible).isFalse()
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

  private class FakeStudioBot(var isContextAllowed: Boolean) : StudioBot.StubStudioBot() {

    override fun isContextAllowed(project: Project) = isContextAllowed
  }
}
