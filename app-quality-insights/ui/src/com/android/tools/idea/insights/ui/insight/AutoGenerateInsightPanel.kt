/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.GenerateInsightsAction.Action
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

class AutoGenerateInsightPanel(
  private val controller: AppInsightsProjectLevelController,
  private val tracker: AppInsightsTracker,
  parentDisposable: Disposable,
) : JPanel(VerticalLayout(16)), Disposable {

  private val appIdFlow =
    controller.state.mapNotNull { it.connections.selected?.appId }.stateIn(createCoroutineScope(), SharingStarted.Eagerly, null)

  init {
    Disposer.register(parentDisposable, this)

    val generateInsight =
      createLink("Generate insight", Action.GENERATE_ONCE) {
        controller.refreshInsight(regenerateWithContext = false, forceGenerateNewInsight = true)
      }
    val enableAutoGenerate =
      createLink("Enable auto-generation", Action.ENABLE_AUTO_GENERATE) {
        controller.aiInsightToolkit.setAutoGenerate(true)
        controller.refreshInsight(false)
      }
    val iconLabel =
      JBLabel(AllIcons.General.Information).apply {
        toolTipText =
          """
          <b>Insight auto-generation</b><br>
          Automatically generate an AI insight for every issue you select using the model selected in the Agent tool window.<br>
          Note: This may increase usage or charges. You can disable this anytime via the gear icon on any generated insight.
          """
            .trimIndent()
      }

    val linksPanel = JPanel(HorizontalLayout(JBUI.scale(16)))
    linksPanel.add(generateInsight)
    linksPanel.add(enableAutoGenerate)
    linksPanel.add(iconLabel)

    add(linksPanel)
  }

  private fun createLink(text: String, action: Action, onClick: () -> Unit) =
    HyperlinkLabel(text).apply {
      addHyperlinkListener {
        onClick()
        appIdFlow.value?.let { tracker.logGenerateInsightAction(it, action) }
      }
    }

  override fun dispose() = Unit
}
