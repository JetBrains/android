/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.DetailedIssueStats
import com.android.tools.idea.insights.LoadingState
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

private const val NOTHING_SELECTED_LABEL = "Select an issue."
private const val MAIN_CARD = "main"
private const val EMPTY_CARD = "empty"

class DetailsPanel(scope: CoroutineScope, insightsState: Flow<AppInsightsState>) :
  JPanel(CardLayout()) {
  private val deviceDistributionPanel =
    DistributionPanel().apply {
      border = BorderFactory.createCompoundBorder(JBUI.Borders.emptyLeft(9), border)
    }
  private val osDistributionPanel =
    DistributionPanel().apply {
      border = BorderFactory.createCompoundBorder(JBUI.Borders.emptyLeft(9), border)
    }

  // The setter has the side effect of updating the UI, so it should be called from the EDT thread.
  private var isDataAvailable: Boolean = false
    set(value) {
      assert(SwingUtilities.isEventDispatchThread())
      if (value != field) {
        (layout as CardLayout).show(this, if (value) MAIN_CARD else EMPTY_CARD)
        field = value
      }
    }

  @VisibleForTesting
  val emptyText =
    AppInsightsStatusText(this) { !isDataAvailable }.apply { appendLine(NOTHING_SELECTED_LABEL) }

  init {
    background = primaryContentBackground
    val headerPanel =
      JPanel().apply {
        background = null
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(0, 8, 0, 0)
        add(TitledSeparator("Devices"))
        add(deviceDistributionPanel)
        add(TitledSeparator("Android Versions"))
        add(osDistributionPanel)
        add(Box.createGlue())
        components.forEach { (it as JComponent).alignmentX = Component.LEFT_ALIGNMENT }
        scope.launch {
          var currentStats: DetailedIssueStats? = null
          insightsState.collect { state ->
            when (val stats = state.currentIssueDetails) {
              is LoadingState.Loading -> {
                isDataAvailable = false
                emptyText.text = "Loading..."
              }
              is LoadingState.Ready -> {
                if (stats.value == null) {
                  isDataAvailable = false
                  setEmptyText()
                } else {
                  isDataAvailable = true
                  if (currentStats != stats.value) {
                    currentStats = stats.value
                    updateDistributions(stats.value)
                  }
                }
              }
              is LoadingState.Failure -> {
                isDataAvailable = false
                emptyText.text = stats.message ?: "Unknown failure"
              }
            }
          }
        }
      }
    val mainPanel = JPanel(BorderLayout())
    mainPanel.background = primaryContentBackground
    mainPanel.add(headerPanel, BorderLayout.NORTH)
    val scrollPane = ScrollPaneFactory.createScrollPane(mainPanel, SideBorder.NONE)
    scrollPane.isFocusable = true
    add(scrollPane, MAIN_CARD)
    add(JPanel().apply { isOpaque = false }, EMPTY_CARD)
    (layout as CardLayout).show(this, EMPTY_CARD)
  }

  private fun updateDistributions(stats: DetailedIssueStats?) {
    deviceDistributionPanel.removeAll()
    osDistributionPanel.removeAll()
    if (stats == null) {
      return
    }
    deviceDistributionPanel.updateDistribution(stats.deviceStats, "device")
    osDistributionPanel.updateDistribution(stats.osStats, "Android version")
  }

  private fun setEmptyText() {
    emptyText.clear()
    emptyText.appendText(NOTHING_SELECTED_LABEL, EMPTY_STATE_TITLE_FORMAT)
    emptyText.appendSecondaryText(
      "Select an issue to view the details.",
      EMPTY_STATE_TEXT_FORMAT,
      null
    )
  }

  override fun updateUI() {
    super.updateUI()
    emptyText?.setFont(StartupUiUtil.getLabelFont())
  }

  override fun paint(g: Graphics?) {
    super.paint(g)
    emptyText.paint(this, g)
  }
}
