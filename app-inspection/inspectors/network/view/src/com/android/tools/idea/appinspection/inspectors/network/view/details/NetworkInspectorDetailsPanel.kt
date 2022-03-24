/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.CloseButton
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * View to display a single network request and its detailed information.
 */
class NetworkInspectorDetailsPanel(
  private val inspectorView: NetworkInspectorView,
  private val scope: CoroutineScope,
  private val usageTracker: NetworkInspectorTracker
) : JPanel(BorderLayout()) {

  @VisibleForTesting
  val connectionDetailsView: ConnectionDetailsView

  val ruleDetailsView: RuleDetailsView

  val cardLayout = CardLayout()
  val cardLayoutView: JPanel

  init {
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    val rootPanel = JPanel(TabularLayout("*,Fit-", "Fit-,*"))

    cardLayoutView = JPanel(cardLayout)
    connectionDetailsView = ConnectionDetailsView(inspectorView, scope, usageTracker)
    ruleDetailsView = RuleDetailsView()
    cardLayoutView.add(connectionDetailsView, ConnectionDetailsView::class.java.name)
    cardLayoutView.add(ruleDetailsView, RuleDetailsView::class.java.name)

    val closeButton = CloseButton { inspectorView.model.resetSelection() }
    // Add a wrapper to move the close button center vertically.
    val closeButtonWrapper = JPanel(BorderLayout())
    closeButtonWrapper.add(closeButton, BorderLayout.CENTER)
    closeButtonWrapper.border = JBEmptyBorder(3, 0, 0, 0)
    rootPanel.add(closeButtonWrapper, TabularLayout.Constraint(0, 1))
    rootPanel.add(cardLayoutView, TabularLayout.Constraint(0, 0, 2, 2))
    add(rootPanel)
  }

  /**
   * Updates the view to show given [httpData].
   */
  fun setHttpData(httpData: HttpData) {
    background = JBColor.background()
    connectionDetailsView.setHttpData(httpData)
    cardLayout.show(cardLayoutView, ConnectionDetailsView::class.java.name)
    isVisible = true
  }

  /**
   * Updates the view to show given [rule].
   */
  fun setRule(rule: RulesTableModel.RuleInfo) {
    background = JBColor.background()
    ruleDetailsView.setRule(rule)
    cardLayout.show(cardLayoutView, RuleDetailsView::class.java.name)
    isVisible = true
  }
}
