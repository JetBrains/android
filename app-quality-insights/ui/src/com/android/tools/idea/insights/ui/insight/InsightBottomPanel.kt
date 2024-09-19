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

import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.insights.experiments.supportsContextSharing
import com.android.tools.idea.insights.ui.INSIGHT_KEY
import com.android.tools.idea.insights.ui.MINIMUM_ACTION_BUTTON_SIZE
import com.android.tools.idea.studiobot.StudioBot
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JButtonAction
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.VisibleForTesting

private const val LEFT_TOOL_BAR = "InsightBottomPanelLeftToolBar"
private const val RIGHT_TOOL_BAR = "InsightBottomPanelRightToolBar"

class InsightBottomPanel(
  private val project: Project,
  private val onEnhanceInsight: (Boolean) -> Unit,
) : JPanel(BorderLayout()) {

  private val actionManager: ActionManager
    get() = ActionManager.getInstance()

  // TODO(b/365994514): button lingers on screen after loading of insight.
  @VisibleForTesting
  val enableCodeContextAction =
    object :
      JButtonAction(
        "Enable Code Context",
        "Grant Gemini in Firebase access to your project code",
        null,
      ) {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
          e.getData(INSIGHT_KEY)?.isEnhancedWithCodeContext() == false &&
            canShowEnableContextButton()
      }

      override fun actionPerformed(e: AnActionEvent) {
        val dialogBuilder =
          MessageDialogBuilder.okCancel(
            "Confirm Context Sharing",
            "<html>Android Studio needs to send code and context from " +
              "your project to enhance the insight for this issue.<br>" +
              "Would you like to continue?</html>",
          )
        if (dialogBuilder.ask(e.project)) {
          onEnhanceInsight(true)
          e.presentation.isEnabledAndVisible = false
        }
      }

      override fun createButton() =
        JButton().apply {
          ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          maximumSize = JBDimension(Int.MAX_VALUE, MINIMUM_ACTION_BUTTON_SIZE.height)
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
    val leftGroup = DefaultActionGroup(enableCodeContextAction)
    val leftToolbar = actionManager.createActionToolbar(LEFT_TOOL_BAR, leftGroup, true)
    leftToolbar.targetComponent = this

    val rightGroup = DefaultActionGroup(copyAction, askGeminiAction)
    val rightToolbar = actionManager.createActionToolbar(RIGHT_TOOL_BAR, rightGroup, true)
    rightToolbar.targetComponent = this

    add(leftToolbar.component, BorderLayout.WEST)
    add(rightToolbar.component, BorderLayout.EAST)

    border = JBUI.Borders.customLineTop(JBColor.border())
  }

  private fun canShowEnableContextButton() =
    !StudioBot.getInstance().isContextAllowed(project) &&
      AppInsightsExperimentFetcher.instance
        .getCurrentExperiment(ExperimentGroup.CODE_CONTEXT)
        .supportsContextSharing()
}
