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
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.android.tools.idea.appinspection.inspectors.network.view.constants.STANDARD_FONT
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel

/**
 * View to display a single network request and its detailed information.
 */
class ConnectionDetailsView(private val inspectorView: NetworkInspectorView) : JPanel(BorderLayout()) {
  private val tabsPanel: CommonTabbedPane
  private val tabs = mutableListOf<TabContent>()

  init {
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    val rootPanel = JPanel(TabularLayout("*,Fit-", "Fit-,*"))
    tabsPanel = CommonTabbedPane()
    tabsPanel.font = STANDARD_FONT
    populateTabs()
    tabsPanel.addChangeListener {
      // Repaint required on tab change or else close button sometimes disappears (seen on Mac)
      repaint()
    }
    val closeButton = CloseButton { inspectorView.model.setSelectedConnection(null) }
    // Add a wrapper to move the close button center vertically.
    val closeButtonWrapper = JPanel(BorderLayout())
    closeButtonWrapper.add(closeButton, BorderLayout.CENTER)
    closeButtonWrapper.border = JBEmptyBorder(3, 0, 0, 0)
    rootPanel.add(closeButtonWrapper, TabularLayout.Constraint(0, 1))
    rootPanel.add(tabsPanel, TabularLayout.Constraint(0, 0, 2, 2))
    add(rootPanel)
  }

  private fun populateTabs() {
    tabs.add(OverviewTabContent(inspectorView.componentsProvider))
    tabs.add(ResponseTabContent(inspectorView.componentsProvider))
    tabs.add(RequestTabContent(inspectorView.componentsProvider))
    tabs.add(
      CallStackTabContent(
        inspectorView.componentsProvider.createStackView(inspectorView.model.stackTraceModel)
      )
    )
    tabs.forEach { tab -> tabsPanel.addTab(tab.title, null, tab.component) }
  }

  /**
   * Updates the view to show given data. If `httpData` is `null`, this clears the view
   * and closes it.
   */
  fun setHttpData(httpData: HttpData?) {
    background = JBColor.background()
    tabs.forEach(Consumer { tab: TabContent -> tab.populateFor(httpData) })
    isVisible = httpData != null
    revalidate()
    repaint()
  }
}