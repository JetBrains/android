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
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AgentActionContributor
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.filterReady
import com.android.tools.idea.insights.model.event.Event
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class InsightLinksPanel(
  private val controller: AppInsightsProjectLevelController,
  currentInsightFlow: StateFlow<LoadingState<AiInsight?>>,
  parentDisposable: Disposable,
) : JPanel(BorderLayout()) {
  init {
    val leftPanel = JPanel(HorizontalLayout(JBUI.scale(15)))
    parentDisposable.createCoroutineScope().launch {
      currentInsightFlow
        .filterReady()
        .combine(controller.state) { a, b -> a to b.selectedIssue }
        .collect { (insight, issue) ->
          leftPanel.removeAll()
          if (insight != null && issue != null) {
            createLinks(insight.event, issue, controller.project).forEach { leftPanel.add(it) }
          }
        }
    }
    add(leftPanel, BorderLayout.WEST)
    add(
      InsightToolbarPanel(currentInsightFlow, parentDisposable, controller::submitInsightFeedback),
      BorderLayout.EAST,
    )
  }
}

private fun createLinks(
  event: Event,
  issue: AppInsightsIssue,
  project: Project,
): List<HyperlinkLabel> =
  AgentActionContributor.EP_NAME.extensions.flatMap { ex ->
    ex.provideActions(event, issue, project).map { (name, action) ->
      HyperlinkLabel(name).apply { addHyperlinkListener { action.invoke() } }
    }
  }
