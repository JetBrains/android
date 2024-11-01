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

import com.android.tools.idea.insights.ui.APP_INSIGHTS_TRACKER_KEY
import com.android.tools.idea.insights.ui.FAILURE_TYPE_KEY
import com.android.tools.idea.insights.ui.INSIGHT_KEY
import com.android.tools.idea.insights.ui.MINIMUM_ACTION_BUTTON_SIZE
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.InsightSentiment.Sentiment
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.Icon

class InsightFeedbackPanel : BorderLayoutPanel() {

  private var currentInsightFeedback = InsightFeedback.NONE

  // TODO: Track upvote and downvote clicks
  private val upvoteAction =
    createFeedbackAction(
      icon = AllIcons.Ide.Like,
      text = "Upvote this insight",
      action = { toggleCurrentFeedback(InsightFeedback.THUMBS_UP, it) },
      state = { currentInsightFeedback == InsightFeedback.THUMBS_UP },
    )

  private val downvoteAction =
    createFeedbackAction(
      icon = AllIcons.Ide.Dislike,
      text = "Downvote this insight",
      action = { toggleCurrentFeedback(InsightFeedback.THUMBS_DOWN, it) },
      state = { currentInsightFeedback == InsightFeedback.THUMBS_DOWN },
    )

  init {
    val actionGroup = DefaultActionGroup(upvoteAction, downvoteAction)
    val toolbar =
      ActionManager.getInstance().createActionToolbar("InsightFeedbackPanel", actionGroup, true)
    toolbar.targetComponent = this
    toolbar.minimumButtonSize = MINIMUM_ACTION_BUTTON_SIZE
    add(toolbar.component, BorderLayout.CENTER)
  }

  fun resetFeedback() {
    currentInsightFeedback = InsightFeedback.NONE
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

  private fun toggleCurrentFeedback(feedback: InsightFeedback, e: AnActionEvent) =
    if (currentInsightFeedback != feedback) {
      currentInsightFeedback = feedback
      logFeedback(feedback.toSentiment(), e)
    } else {
      currentInsightFeedback = InsightFeedback.NONE
      logFeedback(InsightFeedback.NONE.toSentiment(), e)
    }

  private fun logFeedback(sentiment: Sentiment, e: AnActionEvent) {
    val tracker = e.getData(APP_INSIGHTS_TRACKER_KEY) ?: return
    val crashType = e.getData(FAILURE_TYPE_KEY)?.toCrashType() ?: return
    val insight = e.getData(INSIGHT_KEY) ?: return

    tracker.logInsightSentiment(sentiment, crashType, insight)
  }

  private enum class InsightFeedback {
    NONE,
    THUMBS_UP,
    THUMBS_DOWN;

    fun toSentiment() =
      when (this) {
        NONE -> Sentiment.UNKNOWN_SENTIMENT
        THUMBS_UP -> Sentiment.THUMBS_UP
        THUMBS_DOWN -> Sentiment.THUMBS_DOWN
      }
  }
}
