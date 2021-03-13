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
import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.AppInspectorTabLaunchSupport
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.ide.LaunchableInspectorTabLaunchParams
import com.android.tools.idea.appinspection.ide.StaticInspectorTabLaunchParams
import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.appProguardedMessage
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.toIncompatibleVersionMessage
import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Return true if the process it represents is inspectable.
 *
 * Currently, a process is deemed inspectable if the device it's running on is O+ and if it's debuggable. The latter condition is
 * guaranteed to be true because transport pipeline only provides debuggable processes, so there is no need to check.
 */
private fun ProcessDescriptor.isInspectable(): Boolean {
  return this.device.apiLevel >= AndroidVersion.VersionCodes.O
}

class AppInspectionView @VisibleForTesting constructor(
  private val project: Project,
  private val apiServices: AppInspectionApiServices,
  private val ideServices: AppInspectionIdeServices,
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val scope: CoroutineScope,
  private val uiDispatcher: CoroutineDispatcher,
  private val artifactService: InspectorArtifactService,
  getPreferredProcesses: () -> List<String>
) : Disposable {
  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))

  @VisibleForTesting
  val inspectorPanel = JPanel(BorderLayout())

  private var tabsChangedListener: (() -> Unit)? = null

  /**
   * This flow emits an event whenever tabs are (re)populated.
   *
   * In its current form, this API is only designed for use by tests and allows only one flow consumer.
   */
  @VisibleForTesting
  val tabsChangedFlow: Flow<Unit> =
    callbackFlow {
      tabsChangedListener = {
        sendBlocking(Unit)
      }
      awaitClose { }
    }

  @VisibleForTesting
  val inspectorTabs = mutableListOf<AppInspectorTabShell>()

  @VisibleForTesting
  val processesModel: ProcessesModel

  @VisibleForTesting
  val selectProcessAction: SelectProcessAction

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
         InspectorArtifactService.instance,
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
    processesModel = ProcessesModel(edtExecutor, apiServices.processNotifier, { it.isInspectable() }, getPreferredProcesses)
    Disposer.register(this, processesModel)
    selectProcessAction = SelectProcessAction(processesModel) {
      scope.launch {
        apiServices.stopInspectors(it)
        processesModel.stop()
      }
    }
    val group = DefaultActionGroup().apply { add(selectProcessAction) }
    val toolbar = ActionManager.getInstance().createActionToolbar("AppInspection", group, true)
    toolbar.setTargetComponent(component)
    component.add(toolbar.component, TabularLayout.Constraint(0, 0))

    component.add(JSeparator().apply {
      minimumSize = Dimension(Int.MAX_VALUE, JBUI.scale(2))
      preferredSize = minimumSize
    }, TabularLayout.Constraint(1, 0))
    component.add(inspectorPanel, TabularLayout.Constraint(2, 0))

    processesModel.addSelectedProcessListeners(edtExecutor) {
      // Force a UI update NOW instead of waiting to poll.
      ActivityTracker.getInstance().inc()

      val selectedProcess = processesModel.selectedProcess
      if (selectedProcess != null && !selectedProcess.isRunning) {
        // If a process was just killed, we'll get notified about that by being sent a dead
        // process. In that case, remove all inspectors except for those that opted-in to stay up
        // in offline mode and those that haven't finished loading.
        inspectorTabs.removeAll { tab ->
          !tab.provider.supportsOffline() || !tab.isComponentSet
        }
      }
      else {
        // If here, either we have no selected process (e.g. we just opened this view) or we got
        // informed of a new, running process. In this case, clear all tabs to make way for all new
        // tabs for the new process.
        inspectorTabs.clear()
      }
      updateUi()
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
  private suspend fun populateTabs(process: ProcessDescriptor) {
    apiServices.disposeClients(project.name)
    currentProcess = process
    launchInspectorTabsForCurrentProcess()
  }

  private val hyperlinkClicked: () -> Unit = {
    AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
    launchInspectorTabsForCurrentProcess(true)
  }

  private fun CoroutineScope.launchInspectorForTab(
    params: LaunchableInspectorTabLaunchParams,
    tab: AppInspectorTabShell,
    force: Boolean
  ) = launch {
    val provider = params.provider
    try {
      val client = apiServices.launchInspector(
        LaunchParameters(
          currentProcess,
          provider.inspectorId,
          params.jar,
          project.name,
          (provider.inspectorLaunchParams as? LibraryInspectorLaunchParams)?.minVersionLibraryCoordinate,
          force
        )
      )
      withContext(uiDispatcher) {
        tab.setComponent(provider.createTab(project, ideServices, currentProcess, client).component)
      }
      launch {
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
      // We don't log but rethrow cancellation exceptions because they are expected as part of the operation. For example: the service
      // cancels all outstanding futures when it is turned off.
      throw e
    }
    catch (e: AppInspectionProcessNoLongerExistsException) {
      // This happens when trying to launch an inspector on a process/device that no longer exists. In that case, we can safely
      // ignore the attempt. We can count on the UI to be refreshed soon to remove the option.
      withContext(uiDispatcher) {
        tab.setComponent(EmptyStatePanel(AppInspectionBundle.message("process.does.not.exist", currentProcess.name)))
      }
    }
    catch (e: AppInspectionLaunchException) {
      // This happens if a user is already interacting with an inspector in another window, or if Studio got killed suddenly and
      // the old inspector is still running.
      withContext(uiDispatcher) {
        tab.setComponent(EmptyStatePanel(AppInspectionBundle.message("inspector.launch.error", provider.displayName)))
        ideServices.showNotification(
          AppInspectionBundle.message("notification.failed.launch", e.message!!),
          severity = AppInspectionIdeServices.Severity.ERROR,
          hyperlinkClicked = hyperlinkClicked
        )
      }
    }
    catch (e: AppInspectionVersionIncompatibleException) {
      withContext(uiDispatcher) { tab.setComponent(EmptyStatePanel(provider.toIncompatibleVersionMessage())) }
    }
    catch (e: AppInspectionLibraryMissingException) {
      withContext(uiDispatcher) { tab.setComponent(EmptyStatePanel(provider.toIncompatibleVersionMessage())) }
    }
    catch (e: AppInspectionAppProguardedException) {
      withContext(uiDispatcher) { tab.setComponent(EmptyStatePanel(appProguardedMessage)) }
    }
    catch (e: Exception) {
      Logger.getInstance(AppInspectionView::class.java).error(e)
    }
  }

  private fun launchInspectorTabsForCurrentProcess(force: Boolean = false) = scope.launch {
    val launchSupport = AppInspectorTabLaunchSupport(getTabProviders, apiServices, project, artifactService)

    // Triage the applicable inspector tab providers into those that can be launched, and those that can't.
    val applicableTabs = try {
      launchSupport.getApplicableTabLaunchParams(currentProcess)
    }
    catch (e: AppInspectionProcessNoLongerExistsException) {
      // Process died before we got a chance to connect, so we won't be launching any inspector tabs this time.
      emptyList()
    }

    val launchableInspectors = applicableTabs
      .filterIsInstance<LaunchableInspectorTabLaunchParams>()
      .associateWith { AppInspectorTabShell(it.provider) }
    val deadInspectorTabs = applicableTabs
      .filterIsInstance<StaticInspectorTabLaunchParams>()
      .map { AppInspectorTabShell(it.provider).also { shell -> shell.setComponent(it.toInfoMessageTab()) } }

    launchableInspectors.forEach { (params, tab) -> launchInspectorForTab(params, tab, force) }

    withContext(uiDispatcher)
    {
      inspectorTabs.clear()
      (launchableInspectors.values + deadInspectorTabs).sorted().forEach { tab ->
        inspectorTabs.add(tab)
      }
      updateUi()
      fireTabsChangedListener()
    }
  }


  private fun fireTabsChangedListener() {
    tabsChangedListener?.invoke()
  }

  @UiThread
  private fun updateUi() {
    inspectorPanel.removeAll()

    // Active inspectors are sorted to the front, so make sure one of them gets default focus
    val inspectorTabsPane = CommonTabbedPane()
    inspectorTabs.forEach { tab -> tab.addTo(inspectorTabsPane) }
    inspectorTabsPane.selectedIndex = if (inspectorTabs.size > 0) 0 else -1

    val inspectorComponent: JComponent = if (inspectorTabs.size > 0) inspectorTabsPane else noInspectorsMessage
    inspectorPanel.add(inspectorComponent)
    inspectorPanel.repaint()
  }

  internal fun isInspectionActive() = processesModel.selectedProcess?.isRunning ?: false

  override fun dispose() {
  }
}