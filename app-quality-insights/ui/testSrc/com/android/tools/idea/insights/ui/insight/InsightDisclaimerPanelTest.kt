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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.Experiment
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import javax.swing.JLabel
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class InsightDisclaimerPanelTest {

  private lateinit var scope: CoroutineScope
  private val insightFlow = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setup() {
    scope = CoroutineScope(EmptyCoroutineContext)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `test disclaimer text`() = runTest {
    insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.ALL_SOURCES)) }

    val disclaimerPanel = InsightDisclaimerPanel(scope, insightFlow)
    val fakeUi = FakeUi(disclaimerPanel)

    delayUntilCondition(200) {
      fakeUi.findVisibleLabel()?.strippedHtmlText() ==
        "This insight was generated with code context."
    }

    insightFlow.update { LoadingState.Ready(AiInsight("", Experiment.CONTROL)) }

    delayUntilCondition(200) {
      fakeUi.findVisibleLabel()?.strippedHtmlText() ==
        "This insight was generated without code context. Enable code context to get better results."
    }
  }

  private fun FakeUi.findVisibleLabel() = findComponent<JLabel> { it.isVisible }

  private fun JLabel.strippedHtmlText() = text.replace("<html>", "").replace("</html>", "")
}
