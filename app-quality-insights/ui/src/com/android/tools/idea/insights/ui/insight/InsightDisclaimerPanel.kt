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

import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.mapReadyOrDefault
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.CardLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val WITHOUT_CODE = "without_code"
private const val WITH_CODE = "with_code"

class InsightDisclaimerPanel(
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
) : JPanel() {
  private val cardLayout = CardLayout()

  private val withoutCodeDisclaimer =
    DisclaimerLabel(
      "This insight was generated without code context. Enable code context to get better results."
    )

  private val withCodeDisclaimer = DisclaimerLabel("This insight was generated with code context.")

  init {
    layout = cardLayout
    border = JBUI.Borders.empty(0, 8)

    add(withoutCodeDisclaimer, WITHOUT_CODE)
    add(withCodeDisclaimer, WITH_CODE)
    cardLayout.show(this, WITHOUT_CODE)

    scope.launch {
      currentInsightFlow
        .mapReadyOrDefault(false) { it?.isEnhancedWithCodeContext() ?: false }
        .collect { isEnhancedWithCodeContext ->
          if (isEnhancedWithCodeContext) {
            cardLayout.show(this@InsightDisclaimerPanel, WITH_CODE)
          } else {
            cardLayout.show(this@InsightDisclaimerPanel, WITHOUT_CODE)
          }
        }
    }
  }

  private inner class DisclaimerLabel(text: String) : JLabel("<html>$text</html>") {
    init {
      font = JBFont.label().asItalic()
      foreground = UIUtil.getLabelDisabledForeground()
    }
  }
}
