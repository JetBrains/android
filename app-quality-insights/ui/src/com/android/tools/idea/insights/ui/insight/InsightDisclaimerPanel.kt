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

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.NamedColorUtil
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class InsightDisclaimerPanel(
  private val controller: AppInsightsProjectLevelController,
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
) : JPanel(VerticalLayout(0)) {

  private val insightToConnectionFlow =
    combine(
        currentInsightFlow.map { it.valueOrNull() },
        controller.state.map { it.connections.selected },
      ) { insight, conn ->
        insight to conn
      }
      .distinctUntilChanged()

  private val geminiOnboardingObserverAction =
    object : AnAction() {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        // This action is never visible
        e.presentation.isEnabledAndVisible = false
        if (
          this@InsightDisclaimerPanel.isVisible &&
            withoutCode.isVisible &&
            GeminiPluginApi.getInstance().isContextAllowed(controller.project)
        ) {
          this@InsightDisclaimerPanel.isVisible = false
          controller.refreshInsight(true)
        }
      }

      override fun actionPerformed(e: AnActionEvent) = Unit
    }

  private val withoutCode =
    disclaimerPanel(
      text =
        "<html>This insight was generated without code context because your current settings do not allow Gemini to use code context. You can change it via <a href='GeminiContextSettings'>Settings > Gemini > Context Awareness</a>.</html>",
      hyperlinkActivated = {
        ShowSettingsUtil.getInstance().showSettingsDialog(
          controller.project,
          { c: Configurable -> c.displayName == "Gemini" },
        ) { configurable ->
          val runnableReference = AtomicReference<Runnable>()
          val component = configurable.createComponent()
          val runnable = {
            if (component?.parent == null) {
              // The component isn't completely set up right away, and we need to be able to iterate
              // up the hierarchy to get the search box. Reschedule the runnable until it's
              // attached.
              ApplicationManager.getApplication().invokeLater(runnableReference.get())
            } else {
              // shouldn't be null, but at least don't blow up if it is. We just won't get
              // highlighting.
              DataManager.getInstance()
                .getDataContext(component)
                .getData(SearchTextField.KEY)
                ?.text = "Use context from this project to improve responses"
            }
          }
          runnableReference.set(runnable)
          ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
        }
      },
    )

  private val projectMismatch =
    disclaimerPanel(
      text =
        "This insight was generated without code context because the currently open project does not appear to match the project selected in ${controller.provider.displayName}"
    ) { /* no link in text */
    }

  init {
    val toolbar =
      ActionManager.getInstance()
        .createActionToolbar(
          "GeminiOnboardingObserver",
          DefaultActionGroup(geminiOnboardingObserverAction),
          true,
        )
    toolbar.targetComponent = this

    scope.launch {
      insightToConnectionFlow.collect { (insight, conn) ->
        if (insight == null || conn == null) {
          return@collect
        }
        when {
          !insight.isEnhancedWithCodeContext() -> {
            isVisible = true
            withoutCode.isVisible = true
            projectMismatch.isVisible = false
          }
          !conn.isMatchingProject() -> {
            isVisible = true
            projectMismatch.isVisible = true
            withoutCode.isVisible = false
          }
          else -> {
            isVisible = false
            withoutCode.isVisible = false
            projectMismatch.isVisible = false
          }
        }
      }
    }

    add(toolbar.component)
    add(withoutCode)
    add(projectMismatch)
  }

  private fun disclaimerPanel(text: String, hyperlinkActivated: (HyperlinkEvent) -> Unit) =
    JTextPane().apply {
      HtmlLabel.setUpAsHtmlLabel(this)
      this.text = text
      addHyperlinkListener(
        object : HyperlinkAdapter() {
          override fun hyperlinkActivated(e: HyperlinkEvent) {
            hyperlinkActivated(e)
          }
        }
      )
      font = JBFont.label().asItalic()
      foreground = NamedColorUtil.getInactiveTextColor()
      isVisible = false
    }
}
