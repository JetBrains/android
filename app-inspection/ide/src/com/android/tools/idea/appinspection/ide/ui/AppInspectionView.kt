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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.model.AndroidModuleInfo
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JPanel

class AppInspectionView(private val project: Project, private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost) {
  val component = JPanel(BorderLayout())

  /**
   * This dictates the names of the preferred processes. They are drawn from the android applicationIds of the modules in this [project].
   */
  private val preferredProcesses: List<String>
    get() = ModuleManager.getInstance(project).modules
      .mapNotNull { AndroidModuleInfo.getInstance(it)?.`package` }
      .toList()

  init {
    component.border = AdtUiUtils.DEFAULT_RIGHT_BORDER

    val inspectionProcessesComboBox =
      AppInspectionProcessesComboBox(AppInspectionProcessesComboBoxModel(appInspectionDiscoveryHost, preferredProcesses))
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
    appInspectionDiscoveryHost.attachToProcess(descriptor).transform { target ->
      AppInspectorTabProvider.EP_NAME.extensionList
        .filter { provider -> provider.isApplicable() }
        .forEach { provider ->
          target.launchInspector(provider.inspectorId, provider.inspectorAgentJar) { messenger ->
            val tab = invokeAndWaitIfNeeded {
              provider.createTab(project, messenger)
                .also { tab -> tabbedPane.addTab(provider.displayName, tab.component) }
            }
            tab.client
          }.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<AppInspectorClient> {
            override fun onSuccess(result: AppInspectorClient?) {
            }
            override fun onFailure(t: Throwable) {
              Logger.getInstance(AppInspectionView::class.java).error(t)
            }
          })
        }
    }
  }
}