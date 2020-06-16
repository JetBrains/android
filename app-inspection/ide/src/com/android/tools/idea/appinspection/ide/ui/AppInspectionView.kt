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
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessNoLongerExistsException
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.ide.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.CancellationException
import javax.swing.JPanel
import javax.swing.JSeparator

class AppInspectionView(
  private val project: Project,
  private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost,
  private val ideServices: AppInspectionIdeServices,
  getPreferredProcesses: () -> List<String>
) : Disposable {
  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))
  private val inspectorPanel = JPanel(BorderLayout())

  @VisibleForTesting
  val inspectorTabs = CommonTabbedPane()

  @VisibleForTesting
  val processModel: AppInspectionProcessModel

  private val noInspectorsMessage = EmptyStatePanel(AppInspectionBundle.message("select.process"))

  private fun showCrashNotification(inspectorName: String) {
    ideServices.showNotification(
      AppInspectionBundle.message("notification.crash", inspectorName),
      severity = AppInspectionIdeServices.Severity.ERROR
    ) {
      AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
      launchInspectorTabsForCurrentProcess()
    }
  }

  private lateinit var currentProcess: ProcessDescriptor

  init {
    component.border = AdtUiUtils.DEFAULT_RIGHT_BORDER
    val edtExecutor = EdtExecutorService.getInstance()
    processModel = AppInspectionProcessModel(edtExecutor, appInspectionDiscoveryHost, getPreferredProcesses)
    Disposer.register(this, processModel)
    val group = DefaultActionGroup().apply { add(SelectProcessAction(processModel)) }
    val toolbar = ActionManager.getInstance().createActionToolbar("AppInspection", group, true)
    toolbar.setTargetComponent(component)
    component.add(toolbar.component, TabularLayout.Constraint(0, 0))

    component.add(JSeparator().apply {
      minimumSize = Dimension(Int.MAX_VALUE, JBUI.scale(2))
      preferredSize = minimumSize
    }, TabularLayout.Constraint(1, 0))
    component.add(inspectorPanel, TabularLayout.Constraint(2, 0))

    processModel.addSelectedProcessListeners(edtExecutor) {
      // Force a UI update NOW instead of waiting to poll.
      ActivityTracker.getInstance().inc()
      clearTabs()
      processModel.selectedProcess?.let {
        populateTabs(it)
      }
    }
    updateUi()
  }

  @UiThread
  private fun clearTabs() {
    inspectorTabs.removeAll()
    appInspectionDiscoveryHost.disposeClients(project.name)
    updateUi()
  }

  @UiThread
  private fun populateTabs(process: ProcessDescriptor) {
    currentProcess = process
    launchInspectorTabsForCurrentProcess()
    updateUi()
  }

  private fun launchInspectorTabsForCurrentProcess() {
    AppInspectorTabProvider.EP_NAME.extensionList
      .filter { provider -> provider.isApplicable() }
      .forEach { provider ->
        appInspectionDiscoveryHost.launchInspector(
          AppInspectionDiscoveryHost.LaunchParameters(
            currentProcess,
            provider.inspectorId,
            provider.inspectorAgentJar,
            project.name
          )
        ) { messenger ->
          invokeAndWaitIfNeeded {
            provider.createTab(project, messenger, ideServices)
              .also { tab -> inspectorTabs.addTab(provider.displayName, tab.component) }
              .also { updateUi() }
          }.client
        }.transform { client ->
          client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
            override fun onCrashEvent(message: String) {
              AppInspectionAnalyticsTrackerService.getInstance(project).trackErrorOccurred(AppInspectionEvent.ErrorKind.INSPECTOR_CRASHED)
              showCrashNotification(provider.displayName)
            }
          }, MoreExecutors.directExecutor())
        }.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<Unit> {
          override fun onSuccess(result: Unit?) {}
          override fun onFailure(t: Throwable) {
            // We don't log cancellation exceptions because they are expected as part of the operation. For example: the service cancels all
            // outstanding futures when it is turned off.
            if (t !is CancellationException
                // This happens when trying to launch an inspector on a process/device that no longer exists. In that case, we can safely
                // ignore the attempt. We can count on the UI to be refreshed soon to remove the option.
                && t !is ProcessNoLongerExistsException) {
              Logger.getInstance(AppInspectionView::class.java).error(t)
            }
          }
        })
      }
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
    inspectorPanel.repaint()
  }

  override fun dispose() {
  }
}