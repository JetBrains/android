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
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
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

@RunsInEdt
class InsightBottomPanelTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(EdtRule()).around(projectRule).around(controllerRule)

  private lateinit var copyProvider: FakeCopyProvider
  private lateinit var testEvent: AnActionEvent
  private lateinit var fakeUi: FakeUi
  private val scope = CoroutineScope(EmptyCoroutineContext)
  private val currentInsightFlow =
    MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

  @Before
  fun setup() {
    copyProvider = FakeCopyProvider()
    currentInsightFlow.update { LoadingState.Ready(null) }
    testEvent =
      TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.COPY_PROVIDER.`is`(it) -> copyProvider
          else -> null
        }
      }
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

  private class FakeCopyProvider(var text: String = "") : CopyProvider {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun performCopy(dataContext: DataContext) = Unit

    override fun isCopyEnabled(dataContext: DataContext) = text.isNotBlank()

    override fun isCopyVisible(dataContext: DataContext) = true
  }

  private fun createInsightBottomPanel() =
    InsightBottomPanel(controllerRule.controller, scope, currentInsightFlow).also {
      fakeUi = FakeUi(it)
    }
}
