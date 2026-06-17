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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.insights.AppInsightsCrashController
import com.android.tools.idea.insights.AppInsightsCrashState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AgentActionContributor
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.filterReady
import com.android.tools.idea.insights.model.event.Event
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class InsightLinksPanel(
  private val controller: AppInsightsCrashController,
  currentInsightFlow: StateFlow<LoadingState<AiInsight?>>,
  tracker: AppInsightsTracker,
  parentDisposable: Disposable,
) : JPanel(BorderLayout()) {
  private val scope = parentDisposable.createCoroutineScope()

  init {
    val leftPanel = JPanel(HorizontalLayout(JBUI.scale(15)))
    scope.launch {
      currentInsightFlow
        .filterReady()
        .combine(controller.state) { a, b -> a to b }
        .collect { (insight, state) ->
          leftPanel.removeAll()
          if (insight != null && state.selectedIssue != null) {
            createLinks(insight.event, state, controller.project, tracker, scope).forEach { leftPanel.add(it) }
          }
        }
    }
    add(leftPanel, BorderLayout.WEST)
    add(InsightToolbarPanel(controller, currentInsightFlow, parentDisposable, controller::submitInsightFeedback), BorderLayout.EAST)
  }
}

private fun createLinks(
  event: Event,
  state: AppInsightsCrashState,
  project: Project,
  tracker: AppInsightsTracker,
  scope: CoroutineScope,
): List<HyperlinkLabel> =
  AgentActionContributor.EP_NAME.extensions.flatMap { ex ->
    val issue = state.selectedIssue ?: return@flatMap emptyList()
    ex.provideActions(event, issue, project).map { action ->
      HyperlinkLabel(action.name).apply {
        addHyperlinkListener {
          scope.launch {
            action.action.invoke()
            val connection = state.connections.selected ?: return@launch
            tracker.logAgentAction(action.metricsEvent, connection.appId, issue.issueDetails.fatality)
          }
        }
        isFocusable = true
      }
    }
  }
