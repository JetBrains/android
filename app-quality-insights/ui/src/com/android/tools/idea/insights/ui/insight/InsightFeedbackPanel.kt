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

import com.android.tools.idea.insights.experiments.InsightFeedback
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.Icon
import kotlinx.coroutines.flow.StateFlow

class InsightFeedbackPanel(
  private val feedbackState: StateFlow<InsightFeedback>,
  private val onSubmitFeedback: (InsightFeedback) -> Unit,
) : BorderLayoutPanel() {

  private val upvoteAction =
    createFeedbackAction(
      icon = StudioIcons.Common.LIKE,
      text = "Upvote this insight",
      action = { toggleCurrentFeedback(InsightFeedback.THUMBS_UP) },
      state = { feedbackState.value == InsightFeedback.THUMBS_UP },
    )

  private val downvoteAction =
    createFeedbackAction(
      icon = StudioIcons.Common.DISLIKE,
      text = "Downvote this insight",
      action = { toggleCurrentFeedback(InsightFeedback.THUMBS_DOWN) },
      state = { feedbackState.value == InsightFeedback.THUMBS_DOWN },
    )

  init {
    val actionGroup = DefaultActionGroup(upvoteAction, downvoteAction)
    val toolbar =
      ActionManager.getInstance().createActionToolbar("InsightFeedbackPanel", actionGroup, true)
    toolbar.targetComponent = this
    add(toolbar.component, BorderLayout.CENTER)
  }

  fun resetFeedback() {
    // This causes the actions to update/reset.
    ActivityTracker.getInstance().inc()
  }

  private fun createFeedbackAction(
    icon: Icon,
    text: String,
    action: (AnActionEvent) -> Unit,
    state: () -> Boolean,
  ) =
    object : DumbAwareAction(text, null, icon), Toggleable {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        Toggleable.setSelected(e.presentation, state())
      }

      override fun actionPerformed(e: AnActionEvent) {
        action(e)
      }
    }

  private fun toggleCurrentFeedback(feedback: InsightFeedback) =
    if (feedbackState.value != feedback) {
      onSubmitFeedback(feedback)
    } else {
      onSubmitFeedback(InsightFeedback.NONE)
    }
}
