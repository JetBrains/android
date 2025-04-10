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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.android.tools.idea.appinspection.inspectors.network.view.constants.STANDARD_FONT
import org.jetbrains.annotations.VisibleForTesting

internal class ConnectionDataDetailsView(
  private val inspectorView: NetworkInspectorView,
  private val usageTracker: NetworkInspectorTracker,
) : CommonTabbedPane() {

  @VisibleForTesting val tabs = mutableListOf<TabContent>()

  init {
    font = STANDARD_FONT
    populateTabs()
    addChangeListener {
      when (selectedIndex) {
        1 -> usageTracker.trackResponseTabSelected()
        2 -> usageTracker.trackRequestTabSelected()
        3 -> usageTracker.trackCallstackTabSelected()
      }
      // Repaint required on tab change or else close button sometimes disappears (seen on Mac)
      repaint()
    }
  }

  private fun populateTabs() {
    tabs.add(OverviewTabContent())
    tabs.add(ResponseTabContent())
    tabs.add(RequestTabContent())
    tabs.add(
      CallStackTabContent(
        inspectorView.componentsProvider.createStackView(inspectorView.model.stackTraceModel)
      )
    )
    tabs.forEach { tab -> addTab(tab.title, null, tab.component) }
  }

  /** Updates the view to show given data. */
  fun setConnectionData(data: ConnectionData?) {
    val dataComponentFactory =
      when (data) {
        null -> NullDataComponentFactory()
        is HttpData -> HttpDataComponentFactory(data, inspectorView.componentsProvider)
        is GrpcData -> GrpcDataComponentFactory(inspectorView.project, inspectorView, data)
        else -> throw IllegalArgumentException("Unsupported data type: ${data::class.java.name}")
      }
    tabs.forEach { it.populateFor(data, dataComponentFactory) }
  }

  private class NullDataComponentFactory : DataComponentFactory(null) {
    override fun createDataViewer(type: ConnectionType, formatted: Boolean) = null

    override fun createBodyComponent(type: ConnectionType) = null
  }
}
