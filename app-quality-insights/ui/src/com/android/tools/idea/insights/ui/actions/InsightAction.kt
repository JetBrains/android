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

import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.ui.REQUEST_SOURCE_KEY
import com.android.tools.idea.insights.ui.SELECTED_EVENT_KEY
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.ui.JButtonAction
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
object InsightAction :
  JButtonAction(
    "Enable insights",
    "Complete Gemini onboarding to enable insights",
    StudioIcons.StudioBot.LOGO,
  ) {

  private val geminiPluginId: PluginId?
    get() = PluginManagerCore.plugins.firstOrNull { it.name == "Gemini" }?.pluginId

  private fun JButton.setTooltipAndText() =
    if (StudioBot.getInstance().isAvailable()) {
      text = "Show insights"
      toolTipText = "Show insights for this issue"
    } else {
      text = "Enable insights"
      toolTipText = "Complete Gemini onboarding to enable insights"
    }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply {
      // Reset the property set by JButtonAction. It makes the button appear squished.
      putClientProperty("ActionToolbar.smallVariant", false)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
    if (component is JButton) {
      component.setTooltipAndText()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val source = e.getData(REQUEST_SOURCE_KEY) ?: return
    val selectedEvent = e.getData(SELECTED_EVENT_KEY) ?: return
    val pluginId = geminiPluginId ?: return

    // TODO(b/338142913): Remove once Gemini opens plugin window
    if (PluginManagerCore.isDisabled(pluginId)) {
      PluginManagerConfigurable.showPluginConfigurable(project, listOf(pluginId))
    } else {
      StudioBot.getInstance().chat(project).stageChatQuery(createPrompt(selectedEvent), source)
    }
  }

  private fun createPrompt(event: Event) =
    String.format(
        CRASHLYTICS_GEMINI_PROMPT_FORMAT,
        event.deviceName,
        event.apiLevel,
        event.stackTrace(),
      )
      .trim()

  private val Event.deviceName: String
    get() = eventData.device.let { "${it.manufacturer} ${it.model}" }

  private val Event.apiLevel: String
    get() = eventData.operatingSystemInfo.displayVersion

  private fun Event.stackTrace() =
    buildString {
        stacktraceGroup.exceptions.forEachIndexed { idx, exception ->
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
