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

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.android.tools.idea.insights.experiments.supportsContextSharing
import com.android.tools.idea.insights.mapReadyOrDefault
import com.android.tools.idea.insights.ui.MINIMUM_ACTION_BUTTON_SIZE
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JButtonAction
import icons.StudioIcons
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LEFT_TOOL_BAR = "InsightBottomPanelLeftToolBar"
private const val RIGHT_TOOL_BAR = "InsightBottomPanelRightToolBar"

class InsightBottomPanel(
  private val controller: AppInsightsProjectLevelController,
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
  private val onEnhanceInsight: (Boolean) -> Unit,
) : JPanel() {

  private val actionManager: ActionManager
    get() = ActionManager.getInstance()

  private val feedbackPanel =
    InsightFeedbackPanel(
      currentInsightFlow
        .mapReadyOrDefault(InsightFeedback.NONE) { insight ->
          insight?.feedback ?: InsightFeedback.NONE
        }
        .stateIn(scope, SharingStarted.Eagerly, InsightFeedback.NONE)
    ) {
      controller.submitInsightFeedback(it)
    }

  private val enableCodeContextButton =
    object : JButton("Use code context") {
      init {
        toolTipText = "Grant Gemini in Firebase access to your project code"
        isVisible = false

        ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)

        addActionListener {
          val dialogBuilder =
            MessageDialogBuilder.okCancel(
              "Confirm Context Sharing",
              "<html>Android Studio needs to send code and context from " +
                "your project to enhance the insight for this issue.<br>" +
                "Would you like to continue?</html>",
            )
          if (dialogBuilder.ask(this@InsightBottomPanel)) {
            onEnhanceInsight(true)
            isVisible = false
          }
        }
      }
    }

  private val copyAction = actionManager.getAction(IdeActions.ACTION_COPY)

  private val askGeminiAction: AnAction =
    object : JButtonAction("Ask Gemini", null, StudioIcons.StudioBot.ASK), CustomComponentAction {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        // TODO: Show the action when ask Gemini is supported
        e.presentation.isEnabledAndVisible = false
      }

      override fun actionPerformed(e: AnActionEvent) {
        // TODO: Pass chat context to Gemini plugin
      }

      override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
        when (place) {
          LEFT_TOOL_BAR ->
            JButton().apply {
              text = presentation.text
              icon = presentation.icon
              isFocusable = false
              addActionListener { performAction(this, place, presentation) }
            }
          RIGHT_TOOL_BAR -> ActionButton(this, presentation, place, MINIMUM_ACTION_BUTTON_SIZE)
          else -> throw IllegalArgumentException("Ask Gemini cannot be placed in this location")
        }
    }

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    val rightGroup = DefaultActionGroup(copyAction, askGeminiAction)
    val rightToolbar = actionManager.createActionToolbar(RIGHT_TOOL_BAR, rightGroup, true)
    rightToolbar.targetComponent = this

    add(enableCodeContextButton)
    add(feedbackPanel)
    add(rightToolbar.component)

    border = JBUI.Borders.customLineTop(JBColor.border())

    scope.launch {
      currentInsightFlow.distinctUntilChanged().collect { state ->
        when (state) {
          is LoadingState.Ready -> {
            feedbackPanel.resetFeedback()
            enableCodeContextButton.isVisible = canShowEnableContextButton(state)
          }
          else -> {
            enableCodeContextButton.isVisible = false
          }
        }
      }
    }
  }

  private fun canShowEnableContextButton(state: LoadingState.Ready<AiInsight?>) =
    state.value?.isEnhancedWithCodeContext() == false &&
      !GeminiPluginApi.getInstance().isContextAllowed(controller.project) &&
      AppInsightsExperimentFetcher.instance
        .getCurrentExperiment(ExperimentGroup.CODE_CONTEXT)
        .supportsContextSharing()
}
