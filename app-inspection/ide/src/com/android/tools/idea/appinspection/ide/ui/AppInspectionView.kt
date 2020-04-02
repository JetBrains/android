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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessesComboBoxModel
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class AppInspectionView(
  private val project: Project,
  private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost,
  getPreferredProcesses: () -> List<String>
) : Disposable {
  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))
  private val inspectorPanel = JPanel(BorderLayout())

  @VisibleForTesting
  val inspectorTabs = CommonTabbedPane()
  private val noInspectorsMessage =
    JPanel(BorderLayout()).apply {
      add(JBLabel("Please select or launch a process to continue.", SwingConstants.CENTER).apply {
        font = AdtUiUtils.DEFAULT_FONT.biggerOn(3f)
        foreground = UIUtil.getInactiveTextColor()
      })
    }

  private val activeClients = CopyOnWriteArrayList<AppInspectorClient.CommandMessenger>()

  init {
    component.border = AdtUiUtils.DEFAULT_RIGHT_BORDER
    
    val comboBoxModel = AppInspectionProcessesComboBoxModel(appInspectionDiscoveryHost, getPreferredProcesses)
    Disposer.register(this, comboBoxModel)

    val inspectionProcessesComboBox = AppInspectionProcessesComboBox(comboBoxModel)
    val toolbar = JPanel(BorderLayout())
    toolbar.add(inspectionProcessesComboBox, BorderLayout.WEST)
    component.add(toolbar, TabularLayout.Constraint(0, 0))
    component.add(JSeparator().apply {
      minimumSize = Dimension(Int.MAX_VALUE, JBUI.scale(2))
      preferredSize = minimumSize
    }, TabularLayout.Constraint(1, 0))
    component.add(inspectorPanel, TabularLayout.Constraint(2, 0))


    inspectionProcessesComboBox.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        refreshTabs(e)
      }
    }

    updateUi()
  }

  @UiThread
  private fun refreshTabs(itemEvent: ItemEvent) {
    inspectorTabs.removeAll()

    activeClients.removeAll {
      it.disposeInspector()
      true
    }
    (itemEvent.item as? ProcessDescriptor)?.let { descriptor ->
      appInspectionDiscoveryHost.attachToProcess(descriptor).transform { target ->
        AppInspectorTabProvider.EP_NAME.extensionList
          .filter { provider -> provider.isApplicable() }
          .forEach { provider ->
            target.launchInspector(provider.inspectorId, provider.inspectorAgentJar) { messenger ->
              val tab = invokeAndWaitIfNeeded {
                provider.createTab(project, messenger)
                  .also { tab -> inspectorTabs.addTab(provider.displayName, tab.component) }
                  .also { updateUi() }
              }
              activeClients.add(messenger)
              tab.client
            }.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<AppInspectorClient> {
              override fun onSuccess(result: AppInspectorClient?) {}
              override fun onFailure(t: Throwable) = Logger.getInstance(AppInspectionView::class.java).error(t)
            })
          }
      }
    }

    updateUi()
  }

  private fun updateUi() {
    inspectorPanel.removeAll()

    val inspectorComponent = when (inspectorTabs.tabCount) {
      0 -> noInspectorsMessage
      // TODO(b/152556591): Remove this case once we launch more than one inspector
      1 -> inspectorTabs.getComponentAt(0)
      else -> inspectorTabs
    }
    inspectorPanel.add(inspectorComponent)
  }

  override fun dispose() {
  }
}