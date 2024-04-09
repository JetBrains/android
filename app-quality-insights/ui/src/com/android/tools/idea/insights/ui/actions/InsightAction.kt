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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.JButton
import javax.swing.JComponent

private val CRASHLYTICS_GEMINI_PROMPT_FORMAT =
  """
    Explain this exception from my app running on %s with Android version %s:
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

/**
 * [AnAction] that updates the text and tooltip depending on the availability of Gemini.
 *
 * When clicked:
 * * If Gemini is available, creates a sample prompt and stages it in Gemini toolwindow.
 * * If Gemini is not available, opens the Gemini toolwindow for user to finish onboarding.
 */
class InsightAction(
  private val studioBotRequestSource: StudioBot.RequestSource,
  private val projectFetcher: () -> Project? = { null },
  private val currentIssueFetcher: () -> AppInsightsIssue? = { null },
) : AnAction(), CustomComponentAction {

  private fun JButton.setTooltipAndText() =
    if (StudioBot.getInstance().isAvailable()) {
      text = "Show insights"
      toolTipText = "Show insights for this issue"
      isEnabled = true
    } else {
      text = "Enable insights"
      toolTipText = "Complete Gemini onboarding to enable insights"
    }

  private val button =
    JButton().apply {
      icon = StudioIcons.StudioBot.LOGO
      setTooltipAndText()
      addActionListener {
        val project = projectFetcher() ?: return@addActionListener
        val issue = currentIssueFetcher() ?: return@addActionListener
        StudioBot.getInstance()
          .chat(project)
          .stageChatQuery(createPrompt(issue), studioBotRequestSource)
      }
    }

  val component: JComponent
    get() = button

  override fun createCustomComponent(presentation: Presentation, place: String) = button

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) =
    button.setTooltipAndText()

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  // ActionListener on the JButton handles the click
  override fun actionPerformed(e: AnActionEvent) = Unit

  private fun createPrompt(issue: AppInsightsIssue) =
    String.format(
        CRASHLYTICS_GEMINI_PROMPT_FORMAT,
        issue.deviceName,
        issue.apiLevel,
        issue.stackTrace(),
      )
      .trim()

  private val AppInsightsIssue.deviceName: String
    get() = sampleEvent.eventData.device.let { "${it.manufacturer} ${it.model}" }

  private val AppInsightsIssue.apiLevel: String
    get() = sampleEvent.eventData.operatingSystemInfo.displayVersion

  private fun AppInsightsIssue.stackTrace() =
    buildString {
        sampleEvent.stacktraceGroup.exceptions.forEachIndexed { idx, exception ->
          if (idx == 0 || exception.rawExceptionMessage.startsWith("Caused by")) {
            appendLine(exception.rawExceptionMessage)
            append(
              exception.stacktrace.frames.joinToString(separator = "") { "\t${it.rawSymbol}\n" }
            )
          }
        }
      }
      .trim()
}
