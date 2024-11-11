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
import com.android.tools.idea.insights.mapReadyOrDefault
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class InsightDisclaimerPanel(
  private val controller: AppInsightsProjectLevelController,
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
) : JPanel() {
  private val borderLayout =
    object : BorderLayout() {
      override fun preferredLayoutSize(parent: Container): Dimension? {
        val current = findCurrentComponent(parent) ?: return super.preferredLayoutSize(parent)
        val insets = parent.insets
        val pref = current.preferredSize
        pref.width += insets.left + insets.right
        pref.height += insets.top + insets.bottom
        return pref
      }

      private fun findCurrentComponent(parent: Container) = parent.components.first { it.isVisible }
    }

  private val geminiOnboardingObserverAction =
    object : AnAction() {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        // This action is never visible
        e.presentation.isEnabledAndVisible = false
        if (
          this@InsightDisclaimerPanel.isVisible &&
            GeminiPluginApi.getInstance().isContextAllowed(controller.project)
        ) {
          this@InsightDisclaimerPanel.isVisible = false
          controller.refreshInsight(true)
        }
      }

      override fun actionPerformed(e: AnActionEvent) = Unit
    }

  private val withoutCodeDisclaimer =
    disclaimerPanel(
      text =
        "<html>This insight was generated without code context because your current settings do not allow Gemini to use code context. You can change it via <a href='GeminiContextSettings'>Settings > Gemini > Context Awareness</a>.</html>",
      hyperlinkActivated = {
        ShowSettingsUtil.getInstance().showSettingsDialog(controller.project, "Gemini")
      },
    )

  private val withoutCodePanel =
    JPanel(VerticalLayout(JBUI.scale(3))).apply {
      name = "without_code_disclaimer_panel"
      add(withoutCodeDisclaimer)
    }

  init {
    layout = borderLayout

    add(withoutCodePanel, BorderLayout.CENTER)

    val toolbar =
      ActionManager.getInstance()
        .createActionToolbar(
          "GeminiOnboardingObserver",
          DefaultActionGroup(geminiOnboardingObserverAction),
          true,
        )
    toolbar.targetComponent = this
    add(toolbar.component, BorderLayout.NORTH)

    scope.launch {
      currentInsightFlow
        .mapReadyOrDefault(false) { it?.isEnhancedWithCodeContext() == true }
        .distinctUntilChanged()
        .collect { isEnhancedWithCodeContext -> isVisible = !isEnhancedWithCodeContext }
    }
  }

  private fun disclaimerPanel(text: String, hyperlinkActivated: (HyperlinkEvent) -> Unit) =
    JEditorPane().apply {
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
    }
}
