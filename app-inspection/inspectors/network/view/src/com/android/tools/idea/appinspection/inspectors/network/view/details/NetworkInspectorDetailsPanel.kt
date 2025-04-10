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
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.stdui.CloseButton
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel.DetailContent.CONNECTION
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleVariablesStateComponent
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/** View to display detailed information of an interception rule or connection. */
internal class NetworkInspectorDetailsPanel(
  inspectorView: NetworkInspectorView,
  usageTracker: NetworkInspectorTracker,
) : JPanel(BorderLayout()) {

  val connectionDataDetailsView: ConnectionDataDetailsView
  val ruleDetailsView: RuleDetailsView

  private val cardLayout = CardLayout()
  private val cardLayoutView: JPanel
  private val aspectObserver = AspectObserver()

  init {
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    val rootPanel = JPanel(TabularLayout("*,Fit-", "Fit-,*"))

    cardLayoutView = JPanel(cardLayout)
    connectionDataDetailsView = ConnectionDataDetailsView(inspectorView, usageTracker)
    val getRuleNames: () -> Set<String> = {
      inspectorView.rulesView.tableModel.items.mapTo(HashSet()) { it.name }
    }
    ruleDetailsView =
      RuleDetailsView(
        getRuleNames,
        RuleVariablesStateComponent.getInstance(inspectorView.project).state.ruleVariables,
        usageTracker,
      )
    cardLayoutView.add(connectionDataDetailsView, CONNECTION.name)
    cardLayoutView.add(ruleDetailsView, NetworkInspectorModel.DetailContent.RULE.name)
    val model = inspectorView.model
    model.aspect.addDependency(aspectObserver).onChange(NetworkInspectorAspect.DETAILS) {
      isVisible = model.detailContent != NetworkInspectorModel.DetailContent.EMPTY
      cardLayout.show(cardLayoutView, model.detailContent.name)
    }
    model.aspect.addDependency(aspectObserver).onChange(
      NetworkInspectorAspect.SELECTED_CONNECTION
    ) {
      usageTracker.trackConnectionDetailsSelected()
      model.selectedConnection?.let { setConnectionData(it) }
      repaint()
    }
    model.aspect.addDependency(aspectObserver).onChange(NetworkInspectorAspect.SELECTED_RULE) {
      model.selectedRule?.let { setRule(it) }
    }

    val closeButton = CloseButton {
      model.detailContent = NetworkInspectorModel.DetailContent.EMPTY
    }
    // Add a wrapper to move the close button center vertically.
    val closeButtonWrapper = JPanel(BorderLayout())
    closeButtonWrapper.add(closeButton, BorderLayout.CENTER)
    closeButtonWrapper.border = JBUI.Borders.emptyTop(3)
    rootPanel.add(closeButtonWrapper, TabularLayout.Constraint(0, 1))
    rootPanel.add(cardLayoutView, TabularLayout.Constraint(0, 0, 2, 2))
    add(rootPanel)
  }

  /** Updates the view to show given [data]. */
  private fun setConnectionData(data: ConnectionData) {
    background = JBColor.background()
    connectionDataDetailsView.setConnectionData(data)
  }

  /** Updates the view to show given [rule]. */
  private fun setRule(rule: RuleData) {
    background = JBColor.background()
    ruleDetailsView.selectedRule = rule
  }
}
