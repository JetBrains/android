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

import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.android.tools.idea.insights.filterReady
import com.android.tools.idea.insights.mapReadyOrDefault
import com.android.tools.idea.insights.ui.MINIMUM_ACTION_BUTTON_SIZE
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

private const val LEFT_TOOL_BAR = "InsightBottomPanelLeftToolBar"
private const val RIGHT_TOOL_BAR = "InsightBottomPanelRightToolBar"

class InsightBottomPanel(
  private val controller: AppInsightsProjectLevelController,
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
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
    add(feedbackPanel)
    add(rightToolbar.component)

    border = SideBorder(JBColor.border(), SideBorder.TOP)

    currentInsightFlow
      .distinctUntilChanged()
      .filterReady()
      .onEach { feedbackPanel.resetFeedback() }
      .launchIn(scope)
  }
}
