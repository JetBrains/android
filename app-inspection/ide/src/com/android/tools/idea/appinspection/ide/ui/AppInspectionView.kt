/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessesComboBoxModel
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.intellij.ide.plugins.newui.HorizontalLayout
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JPanel

class AppInspectionView(private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost) {
  val component = JPanel(BorderLayout())

  init {
    component.border = AdtUiUtils.DEFAULT_RIGHT_BORDER

    val inspectionProcessesComboBox = AppInspectionProcessesComboBox(AppInspectionProcessesComboBoxModel(appInspectionDiscoveryHost))
    val toolbar = JPanel(HorizontalLayout(0))
    toolbar.add(inspectionProcessesComboBox)
    component.add(toolbar, BorderLayout.NORTH)

    val tabbedPane = CommonTabbedPane()
    component.add(tabbedPane, BorderLayout.CENTER)
    inspectionProcessesComboBox.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        refreshTabs(tabbedPane, e)
      }
    }
  }

  private fun refreshTabs(tabbedPane: CommonTabbedPane, itemEvent: ItemEvent) {
    tabbedPane.removeAll()
    val descriptor = itemEvent.item as? ProcessDescriptor ?: return
    val target = appInspectionDiscoveryHost.attachToProcess(descriptor).get() ?: return
    AppInspectorTabProvider.EP_NAME.extensionList
      .filter { provider -> provider.isApplicable() }
      .forEach { provider ->
        target.launchInspector(provider.inspectorId, provider.inspectorAgentJar) { messenger ->
          val tab = provider.createTab(messenger)
          tabbedPane.addTab(provider.displayName, tab.component)
          tab.client
        }
      }
  }
}