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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** [JPanel] that is shown in the [InsightToolWindow] when an insight is available. */
class InsightContentPanel(
  scope: CoroutineScope,
  currentInsightFlow: Flow<AppInsightsState>,
  parentDisposable: Disposable,
) : JPanel(BorderLayout()), Disposable {

  private val insightContentPanel = InsightTextPane()

  private val insightPanel =
    JPanel(BorderLayout()).apply {
      val scrollPane =
        ScrollPaneFactory.createScrollPane(
            insightContentPanel,
            JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
          )
          .apply {
            isOpaque = false
            border = JBUI.Borders.empty()
            background = JBColor.background()
          }

      add(scrollPane, BorderLayout.CENTER)
    }

  private val loadingPanel =
    JBLoadingPanel(BorderLayout(), this).apply {
      border = JBUI.Borders.empty()
      add(insightPanel)
    }

  init {
    Disposer.register(parentDisposable, this)
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    add(loadingPanel, BorderLayout.CENTER)

    scope.launch {
      currentInsightFlow
        .map { it.currentInsight }
        .distinctUntilChanged()
        .collect { state ->
          when (state) {
            is LoadingState.Failure -> {
              // TODO(b/356449524): Handle the failure states gracefully
            }
            is LoadingState.Ready -> {
              loadingPanel.stopLoading()
              insightContentPanel.text = state.value?.rawInsight ?: ""
            }
            is LoadingState.Loading -> {
              loadingPanel.startLoading()
              insightContentPanel.text = ""
            }
          }
        }
    }
  }

  override fun dispose() = Unit
}
