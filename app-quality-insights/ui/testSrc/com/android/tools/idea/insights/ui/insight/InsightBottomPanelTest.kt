/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.ai.transform.CodeTransformationDeterminerImpl
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import javax.swing.JButton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
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

  private lateinit var fakeUi: FakeUi
  private val currentInsightFlow =
    MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

  @Before
  fun setup() {
    currentInsightFlow.update { LoadingState.Ready(null) }
  }

  @Test
  fun `suggest a fix button state depends on generated insight`() = runBlocking {
    createInsightBottomPanel()
    val insight =
      AiInsight(
        rawInsight =
          """
        This is an insight.
        
        The fix should likely be in AndroidManifest.xml.
      """
            .trimIndent()
      )
    currentInsightFlow.value = LoadingState.Ready(insight)

    val button = fakeUi.findComponent<JButton> { it.name == "suggest_a_fix_button" }!!
    waitForCondition(5.seconds) { button.text == "Suggest a fix" }
    assertThat(button.isEnabled).isTrue()

    currentInsightFlow.value = LoadingState.Ready(AiInsight("This is an insight"))
    waitForCondition(5.seconds) { button.text == "No fix available" }
    assertThat(button.isEnabled).isFalse()
  }

  private fun createInsightBottomPanel() =
    InsightBottomPanel(
        controllerRule.controller,
        currentInsightFlow,
        projectRule.disposable,
        CodeTransformationDeterminerImpl(
          projectRule.project,
          FakeCodeContextResolver(listOf(CodeContext("a/b/c", "blah"))),
        ),
      )
      .also { fakeUi = FakeUi(it) }
}
