/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.AppInsightsContentPanel
import com.android.tools.idea.insights.ui.AppInsightsIssuesTableCellRenderer
import com.android.tools.idea.insights.ui.DistributionToolWindow
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import java.awt.CardLayout
import java.awt.Graphics
import javax.swing.JPanel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val MAIN_CARD = "main"
private const val GET_STARTED = "get_started"
private const val VITALS_WORKBENCH_NAME = "AQI_VITALS"

class VitalsContentContainerPanel(
  projectController: AppInsightsProjectLevelController,
  project: Project,
  tracker: AppInsightsTracker,
  parentDisposable: Disposable
) : JPanel(CardLayout()), Disposable {

  private val scope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)

  init {
    Disposer.register(parentDisposable, this)

    background = primaryContentBackground

    val selectProjectText =
      object : StatusText() {
          override fun isStatusVisible() = true
        }
        .apply {
          appendLine("No apps available", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, null)
          appendLine("")
          appendLine(
            "You don't have permission to any Play Console apps.",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
            null
          )
          appendLine("To see data, ", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
          @Suppress("DialogTitleCapitalization")
          appendText("request Play Console permission.", SimpleTextAttributes.LINK_ATTRIBUTES) {
            BrowserUtil.browse("https://play.google.com/console/about/teamandusermanagement/")
          }
        }
    val selectProjectTextPanel =
      object : JPanel() {
        init {
          selectProjectText.attachTo(this)
        }

        override fun paint(g: Graphics?) {
          super.paint(g)
          selectProjectText.paint(this, g)
        }
      }
    add(selectProjectTextPanel, GET_STARTED)

    add(
      AppInsightsContentPanel(
        projectController,
        project,
        this,
        AppInsightsIssuesTableCellRenderer,
        VITALS_WORKBENCH_NAME,
        listOf(DistributionToolWindow.create(VITALS_WORKBENCH_NAME, scope, projectController.state))
      ) {
        VitalsIssueDetailsPanel(projectController, project, it, this, tracker)
      },
      MAIN_CARD
    )

    scope.launch {
      projectController.state
        .map { it.connections.selected }
        .distinctUntilChanged()
        .collect { selected ->
          if (selected == null || !selected.isConfigured) {
            tracker.logZeroState(
              AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.newBuilder()
                .apply {
                  emptyState =
                    AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.EmptyState
                      .NO_ACCESS
                }
                .build()
            )
            (layout as CardLayout).show(this@VitalsContentContainerPanel, GET_STARTED)
          } else {
            (layout as CardLayout).show(this@VitalsContentContainerPanel, MAIN_CARD)
          }
        }
    }
  }

  override fun dispose() = Unit
}
