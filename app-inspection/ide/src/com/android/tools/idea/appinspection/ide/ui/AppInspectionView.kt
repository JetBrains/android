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
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLaunchException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTabbedPane

private const val KEY_SUPPORTS_OFFLINE = "supports.offline"

class AppInspectionView(
  private val project: Project,
  private val apiServices: AppInspectionApiServices,
  private val ideServices: AppInspectionIdeServices,
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val scope: CoroutineScope,
  private val uiDispatcher: CoroutineDispatcher,
  getPreferredProcesses: () -> List<String>
) : Disposable {

  /**
   * All relevant data needed to add a tab into a tabbed pane.
   *
   * This class allows us to collect information up front before adding all tabs into the tab pane,
   * instead of just adding tabs as threads return them. This ensures we can keep a consistent
   * ordering across inspector launches.
   */
  private class TabMetadata(
    val provider: AppInspectorTabProvider,
    val component: JComponent
  ): Comparable<TabMetadata> {
    fun addTo(tabbedPane: JTabbedPane) {
      tabbedPane.addTab(provider.displayName, provider.icon, component)
      component.putClientProperty(KEY_SUPPORTS_OFFLINE, provider.supportsOffline())
    }

    override fun compareTo(other: TabMetadata) = this.provider.displayName.compareTo(other.provider.displayName)
  }

  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))
  private val inspectorPanel = JPanel(BorderLayout())

  /**
   * If set, this listener will be triggered once tabs are (re)populated, after which it will be
   * cleared.
   *
   * In its current form, this API is only designed for use by tests.
   *
   * Note: Listeners will be dispatched on the UI thread, so they can safely query the state of
   * this view.
   */
  @VisibleForTesting
  var tabsChangedOneShotListener: (() -> Unit)? = null
    set(value) {
      if (value != null) {
        check(field == null) { "Attempting to set two one-shot listeners at the same time" }
      }
      field = value
    }

  @VisibleForTesting
  val inspectorTabs = CommonTabbedPane()

  @VisibleForTesting
  val processModel: AppInspectionProcessModel

  private val noInspectorsMessage = EmptyStatePanel(
    AppInspectionBundle.message("select.process"),
    UrlData("Learn more", "https://d.android.com/r/studio-ui/db-inspector-help")
  )

  constructor(project: Project,
              apiServices: AppInspectionApiServices,
              ideServices: AppInspectionIdeServices,
              scope: CoroutineScope,
              uiDispatcher: CoroutineDispatcher,
              getPreferredProcesses: () -> List<String>) :
    this(project,
         apiServices,
         ideServices,
         { AppInspectorTabProvider.EP_NAME.extensionList },
         scope,
         uiDispatcher,
         getPreferredProcesses)

  private fun showCrashNotification(inspectorName: String) {
    ideServices.showNotification(
      AppInspectionBundle.message("notification.crash", inspectorName),
      severity = AppInspectionIdeServices.Severity.ERROR
    ) {
      AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
      scope.launch {
        launchInspectorTabsForCurrentProcess()
      }
    }
  }

  private lateinit var currentProcess: ProcessDescriptor

  init {
    val edtExecutor = EdtExecutorService.getInstance()
    processModel = AppInspectionProcessModel(edtExecutor, apiServices.processNotifier, getPreferredProcesses)
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

      val selectedProcess = processModel.selectedProcess
      if (selectedProcess != null && !selectedProcess.isRunning) {
        // If a process was just killed, we'll get notified about that by being sent a dead
        // process. In that case, remove all inspectors except for those that opted-in to stay up
        // in offline mode.
        inspectorTabs.removeAllTabs { tab -> tab.getClientProperty(KEY_SUPPORTS_OFFLINE) != true }
      }
      else {
        // If here, either we have no selected process (e.g. we just opened this view) or we got
        // informed of a new, running process. In this case, clear all tabs to make way for all new
        // tabs for the new process.
        inspectorTabs.removeAllTabs()
      }
      if (selectedProcess != null && selectedProcess.isRunning) {
        scope.launch { populateTabs(selectedProcess) }
      }
      else {
        // Note: This is fired by populateTabs in the other case
        fireTabsChangedListener()
      }
    }
    updateUi()
  }

  @UiThread
  private fun JTabbedPane.removeAllTabs(shouldRemove: (JComponent) -> Boolean = { true }) {
    var i = 0
    while (i < tabCount) {
      val tab = getComponentAt(i) as JComponent
      if (shouldRemove(tab)) remove(i) else ++i
    }
  }

  @UiThread
  private suspend fun populateTabs(process: ProcessDescriptor) {
    apiServices.disposeClients(project.name)
    currentProcess = process
    launchInspectorTabsForCurrentProcess()
  }

  private suspend fun launchInspectorTabsForCurrentProcess(force: Boolean = false) {
    val tabs = mutableListOf<TabMetadata>()
    val jobs = getTabProviders()
      .filter { provider -> provider.isApplicable() }
      .map { provider ->
        scope.launch {
          try {
            val client = apiServices.launchInspector(
              LaunchParameters(
                currentProcess,
                provider.inspectorId,
                provider.inspectorLaunchParams.inspectorAgentJar,
                project.name,
                (provider.inspectorLaunchParams as? LibraryInspectorLaunchParams)?.targetLibrary,
                force
              )
            )
            withContext(uiDispatcher) {
              tabs.add(TabMetadata(provider, provider.createTab(project, ideServices, currentProcess, client).component))
            }
            scope.launch {
              if (!client.awaitForDisposal()) { // If here, this client was disposed due to crashing
                AppInspectionAnalyticsTrackerService.getInstance(project).trackErrorOccurred(AppInspectionEvent.ErrorKind.INSPECTOR_CRASHED)
                // Wait until AFTER we're disposed before showing the notification. This ensures if
                // the user hits restart, which requests launching a new inspector, it won't reuse
                // the existing client. (Users probably would never hit restart fast enough but it's
                // possible to trigger in tests.)
                showCrashNotification(provider.displayName)
              }
            }
          }
          catch (e: CancellationException) {
            // We don't log cancellation exceptions because they are expected as part of the operation. For example: the service cancels
            // all outstanding futures when it is turned off.
          }
          catch (e: AppInspectionProcessNoLongerExistsException) {
            // This happens when trying to launch an inspector on a process/device that no longer exists. In that case, we can safely
            // ignore the attempt. We can count on the UI to be refreshed soon to remove the option.
          }
          catch (e: AppInspectionLaunchException) {
            // This happens if a user is already interacting with an inspector in another window, or if Studio got killed suddenly and
            // the old inspector is still running.
            ideServices.showNotification(
              AppInspectionBundle.message("notification.failed.launch", e.message!!),
              severity = AppInspectionIdeServices.Severity.ERROR
            ) {
              AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
              scope.launch { launchInspectorTabsForCurrentProcess(true) }
            }
          }
          catch (e: AppInspectionVersionIncompatibleException) {
            withContext(uiDispatcher) { tabs.add(provider.toIncompatibleVersionMessage()) }
          }
          catch (e: AppInspectionLibraryMissingException) {
            withContext(uiDispatcher) { tabs.add(provider.toIncompatibleVersionMessage()) }
          }
          catch (e: Exception) {
            Logger.getInstance(AppInspectionView::class.java).error(e)
          }
        }
      }

    jobs.joinAll()
    withContext(uiDispatcher) {
      inspectorTabs.removeAllTabs()
      tabs.sorted().forEach { tab -> tab.addTo(inspectorTabs) }
      updateUi()
      fireTabsChangedListener()
    }
  }

  private fun AppInspectorTabProvider.toIncompatibleVersionMessage(): TabMetadata {
    val reason = AppInspectionBundle.message("incompatible.version",
                                             (inspectorLaunchParams as LibraryInspectorLaunchParams).targetLibrary.coordinate)
    return TabMetadata(this, EmptyStatePanel(reason))
  }

  private fun fireTabsChangedListener() {
    tabsChangedOneShotListener?.let { listener ->
      // Clear the one-shot before firing the listener, in case the listener itself registers a new
      // listener (or unblocks another thread from doing so)
      tabsChangedOneShotListener = null
      listener()
    }
  }

  private fun updateUi() {
    inspectorPanel.removeAll()

    // Active inspectors are sorted to the front, so make sure one of them gets default focus
    inspectorTabs.selectedIndex = if (inspectorTabs.tabCount > 0) 0 else -1

    val inspectorComponent: JComponent = if (inspectorTabs.tabCount > 0) inspectorTabs else noInspectorsMessage
    inspectorPanel.add(inspectorComponent)
    inspectorPanel.repaint()
  }

  override fun dispose() {
  }
}