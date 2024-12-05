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
package com.android.tools.idea.insights.ui.insight.onboarding

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ai.InsightsOnboardingProvider
import com.android.tools.idea.insights.ui.AppInsightsStatusText
import com.android.tools.idea.insights.ui.EMPTY_STATE_TEXT_FORMAT
import com.android.tools.idea.insights.ui.EMPTY_STATE_TITLE_FORMAT
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EnableInsightPanel(
  scope: CoroutineScope,
  private val selectedConnectionStateFlow: StateFlow<Connection?>,
  insightsOnboardingProvider: InsightsOnboardingProvider,
) : JPanel(GridBagLayout()) {
  private val enableInsightEmptyText =
    AppInsightsStatusText(this) { true }
      .apply {
        appendText("Insights require Gemini", EMPTY_STATE_TITLE_FORMAT)
        appendLine(
          "You can set up Gemini and enable insights via the button below.",
          EMPTY_STATE_TEXT_FORMAT,
          null,
        )
      }

  private val isStudioBotBackend: Boolean
    get() = !StudioFlags.CRASHLYTICS_TITAN_INSIGHT_PROVIDER.get()

  val button =
    JButton("Enable Insights").apply {
      addActionListener {
        selectedConnectionStateFlow.value?.let {
          insightsOnboardingProvider.performOnboardingAction(it)
        }
      }
      if (!isStudioBotBackend) {
        isFocusable = false
      }
    }

  private val gbc =
    GridBagConstraints().apply {
      gridx = 0
      gridy = 0
    }

  init {
    add(enableInsightEmptyText.component, gbc)

    gbc.apply { gridy = 1 }
    enableInsightEmptyText.secondaryComponent.border = JBUI.Borders.emptyBottom(10)
    add(enableInsightEmptyText.secondaryComponent, gbc)

    gbc.apply { gridy = 3 }
    add(button, gbc)

    insightsOnboardingProvider
      .buttonEnabledState()
      .onEach { enabled -> button.isEnabled = enabled }
      .launchIn(scope)
  }
}
