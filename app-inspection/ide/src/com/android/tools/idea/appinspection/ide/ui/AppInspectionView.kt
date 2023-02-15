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
import com.android.tools.adtui.stdui.CommonTabbedPaneUI
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.APP_PROGUARDED_MESSAGE
import com.android.tools.idea.appinspection.ide.AppInspectorTabLaunchSupport
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.ide.InspectorJarTarget
import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.toIncompatibleVersionMessage
import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCrashException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLaunchException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorForcefullyDisposedException
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorMessengerTarget
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.ui.EmptyStatePanel
import com.android.tools.idea.concurrency.createChildScope
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.HierarchyEvent.SHOWING_CHANGED
import javax.swing.JPanel
import javax.swing.JSeparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@VisibleForTesting val TAB_KEY = Key.create<AppInspectorTab>("app.inspector.shell.tab")

/**
 * Return true if the process it represents is inspectable.
 *
 * Currently, a process is deemed inspectable if the device it's running on is O+ and if it's
 * debuggable. The latter condition is guaranteed to be true because transport pipeline only
 * provides debuggable processes, so there is no need to check.
 */
private fun ProcessDescriptor.isInspectable(): Boolean {
  return this.device.apiLevel >= AndroidVersion.VersionCodes.O
}

class AppInspectionView
@VisibleForTesting
constructor(
  private val project: Project,
  private val apiServices: AppInspectionApiServices,
  private val ideServices: AppInspectionIdeServices,
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val scope: CoroutineScope,
  private val uiDispatcher: CoroutineDispatcher,
  private val artifactService: InspectorArtifactService,
  isPreferredProcess: (ProcessDescriptor) -> Boolean = { false }
) : Disposable {
  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))

  @VisibleForTesting val inspectorPanel = JPanel(BorderLayout())

  @get:VisibleForTesting
  var currentProcess: ProcessDescriptor? = null
    private set

  private var tabsChangedListener: (() -> Unit)? = null

  /**
   * This flow emits an event whenever tabs are (re)populated.
   *
   * In its current form, this API is only designed for use by tests and allows only one flow
   * consumer.
   */
  @VisibleForTesting
  val tabsChangedFlow: Flow<Unit> = callbackFlow {
    tabsChangedListener = { trySend(Unit).onClosed { throw IllegalStateException(it) } }
    awaitClose { tabsChangedListener = null }
  }

  @VisibleForTesting val inspectorTabs = mutableListOf<AppInspectorTabShell>()

  @VisibleForTesting val processesModel: ProcessesModel

  private val selectProcessAction: SelectProcessAction

  private lateinit var selectedTabName: String

  /**
   * If enabled, this view will respond to new processes by connecting inspectors to them.
   *
   * This can be useful behavior to toggle when the window is showing vs. minimized.
   *
   * Modifying this property potentially updates the UI, so it should only be changed on the UI
   * thread.
   */
  @VisibleForTesting
  @set:UiThread
  var autoConnects: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        if (value) {
          processesModel.selectedProcess?.let { process ->
            if (process.isRunning &&
                (currentProcess?.isRunning == false || process.pid != currentProcess?.pid)
            ) {
              handleProcessChanged(processesModel.selectedProcess)
            }
          }
        }
      }
    }

  private val noInspectorsMessage =
    EmptyStatePanel(
      AppInspectionBundle.message("select.process"),
      "https://d.android.com/r/studio-ui/app-inspector-help"
    )

  /**
   * The coroutine scope for launching tabs in the current process. Any activity that causes the
   * currently inspected process to change will cause this scope to be cancelled and replaced with a
   * new one.
   */
  private lateinit var tabsLaunchScope: CoroutineScope

  constructor(
    project: Project,
    apiServices: AppInspectionApiServices,
    ideServices: AppInspectionIdeServices,
    scope: CoroutineScope,
    uiDispatcher: CoroutineDispatcher,
    isPreferredProcess: (ProcessDescriptor) -> Boolean = { false }
  ) : this(
    project,
    apiServices,
    ideServices,
    { AppInspectorTabProvider.EP_NAME.extensionList },
    scope,
    uiDispatcher,
    InspectorArtifactService.instance,
    isPreferredProcess
  )

  private fun showCrashNotification(
    inspectorName: String,
    process: ProcessDescriptor,
    tabShell: AppInspectorTabShell
  ) {
    ideServices.showNotification(
      AppInspectionBundle.message("notification.crash", inspectorName),
      severity = AppInspectionIdeServices.Severity.ERROR
    ) {
      AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
      if (currentProcess == process) {
        tabsLaunchScope.launch { launchInspectorForTab(process, tabShell, false) }
      }
    }
  }

  init {
    val edtExecutor = EdtExecutorService.getInstance()
    processesModel =
      ProcessesModel(
        edtExecutor,
        apiServices.processDiscovery,
        { it.isInspectable() },
        isPreferredProcess
      )
    Disposer.register(this, processesModel)
    selectProcessAction = SelectProcessAction(processesModel, onStopAction = { stopInspectors() })
    val group = DefaultActionGroup().apply { add(selectProcessAction) }
    val toolbar = ActionManager.getInstance().createActionToolbar("AppInspection", group, true)
    toolbar.setTargetComponent(component)
    component.add(toolbar.component, TabularLayout.Constraint(0, 0))

    component.add(
      JSeparator().apply {
        minimumSize = Dimension(Int.MAX_VALUE, JBUI.scale(2))
        preferredSize = minimumSize
      },
      TabularLayout.Constraint(1, 0)
    )
    component.add(inspectorPanel, TabularLayout.Constraint(2, 0))

    component.addHierarchyListener { event ->
      if (event.changeFlags.and(SHOWING_CHANGED.toLong()) > 0) {
        autoConnects = component.isShowing
      }
    }

    processesModel.addSelectedProcessListeners(edtExecutor) {
      val newProcess = processesModel.selectedProcess
      if (newProcess != null && newProcess.isRunning && !autoConnects)
        return@addSelectedProcessListeners

      handleProcessChanged(newProcess)
    }
    updateUi()
  }

  @UiThread
  private fun handleProcessChanged(process: ProcessDescriptor?) {
    currentProcess = process
    // Force a UI update NOW instead of waiting to poll.
    ActivityTracker.getInstance().inc()

    refreshCoroutineScope()
    inspectorTabs.forEach { tab ->
      tab.getUserData(TAB_KEY)?.messengers?.forEach { it.scope.cancel() }
    }
    if (process != null && !process.isRunning) {
      // If a process was just killed, we'll get notified about that by being sent a dead
      // process. In that case, remove all inspectors except for those that opted-in to stay up
      // in offline mode and those that haven't finished loading.
      inspectorTabs.removeAll { tab ->
        (!tab.provider.supportsOffline() || !tab.isComponentSet).also { notSupportOffline ->
          if (notSupportOffline) {
            Disposer.dispose(tab)
          }
        }
      }
    } else {
      // If here, either we have no selected process (e.g. we just opened this view) or we got
      // informed of a new, running process. In this case, clear all tabs to make way for all new
      // tabs for the new process.
      inspectorTabs.forEach { tab -> Disposer.dispose(tab) }
      inspectorTabs.clear()
    }
    updateUi()
    if (process != null && process.isRunning) {
      launchInspectorTabsForCurrentProcess(process)
    } else {
      // Note: This is fired by populateTabs in the other case
      fireTabsChangedListener()
    }
  }

  fun stopInspectors() {
    processesModel.stop()
  }

  private fun hyperlinkClicked(
    process: ProcessDescriptor,
    tabShell: AppInspectorTabShell,
    force: Boolean
  ): () -> Unit = {
    AppInspectionAnalyticsTrackerService.getInstance(project).trackInspectionRestarted()
    tabsLaunchScope.launch { launchInspectorForTab(process, tabShell, force) }
  }

  private fun CoroutineScope.launchInspectorForTab(
    process: ProcessDescriptor,
    tabShell: AppInspectorTabShell,
    force: Boolean
  ) = launch {
    val tabTargets = tabShell.tabJarTargets
    val provider = tabTargets.provider
    try {
      val messengers =
        provider.launchConfigs.map { config ->
          when (val jarTarget = tabTargets.targets.getValue(config.id)) {
            is InspectorJarTarget.Resolved -> {
              try {
                val messenger =
                  apiServices.launchInspector(
                    LaunchParameters(
                      process,
                      config.id,
                      jarTarget.jar,
                      project.name,
                      jarTarget.artifactCoordinate?.let { LibraryCompatibility(it) },
                      force
                    )
                  )
                AppInspectorMessengerTarget.Resolved(messenger)
              } catch (e: AppInspectionVersionIncompatibleException) {
                AppInspectorMessengerTarget.Unresolved(provider.toIncompatibleVersionMessage())
              } catch (e: AppInspectionLibraryMissingException) {
                AppInspectorMessengerTarget.Unresolved(provider.toIncompatibleVersionMessage())
              }
            }
            is InspectorJarTarget.Unresolved -> {
              AppInspectorMessengerTarget.Unresolved(jarTarget.error)
            }
          }
        }

      withContext(uiDispatcher) {
        val tab = provider.createTab(project, ideServices, process, messengers, tabShell)
        tabShell.setComponent(tab.component)
        tabShell.putUserData(TAB_KEY, tab)
      }

      if (messengers.any { it is AppInspectorMessengerTarget.Resolved }) {
        if (provider.launchConfigs.size == 1) {
          launch {
            waitAndHandleSingleInspectorTermination(
              messengers.single() as AppInspectorMessengerTarget.Resolved,
              provider,
              tabShell
            )
          }
        } else {
          launch { waitAndHandleInspectorTermination(messengers.toList(), provider, tabShell) }
        }
      }
    } catch (e: CancellationException) {
      // We don't log but rethrow cancellation exceptions because they are expected as part of the
      // operation. For example: the service
      // cancels all outstanding futures when it is turned off.
      throw e
    } catch (e: AppInspectionProcessNoLongerExistsException) {
      // This happens when trying to launch an inspector on a process/device that no longer exists.
      // In that case, we can safely
      // ignore the attempt. We can count on the UI to be refreshed soon to remove the option.
      withContext(uiDispatcher) {
        tabShell.setComponent(
          EmptyStatePanel(
            AppInspectionBundle.message("process.does.not.exist", process.name),
            provider.learnMoreUrl
          )
        )
      }
    } catch (e: AppInspectionLaunchException) {
      // This happens if a user is already interacting with an inspector in another window, or if
      // Studio got killed suddenly and
      // the old inspector is still running.
      withContext(uiDispatcher) {
        tabShell.setComponent(
          EmptyStatePanel(
            AppInspectionBundle.message("inspector.launch.error", provider.displayName),
            provider.learnMoreUrl
          )
        )
        ideServices.showNotification(
          AppInspectionBundle.message("notification.failed.launch", e.message!!),
          severity = AppInspectionIdeServices.Severity.ERROR,
          hyperlinkClicked = hyperlinkClicked(process, tabShell, force)
        )
      }
    } catch (e: AppInspectionAppProguardedException) {
      withContext(uiDispatcher) {
        tabShell.setComponent(EmptyStatePanel(APP_PROGUARDED_MESSAGE, provider.learnMoreUrl))
      }
    } catch (e: Exception) {
      Logger.getInstance(AppInspectionView::class.java).error(e)
    }
  }

  private fun launchInspectorTabsForCurrentProcess(
    process: ProcessDescriptor,
    force: Boolean = false
  ) {
    tabsLaunchScope.launch {
      val launchSupport =
        AppInspectorTabLaunchSupport(getTabProviders, apiServices, project, artifactService)

      // Triage the applicable inspector tab providers into those that can be launched, and those
      // that can't.
      val tabTargetsList =
        try {
          launchSupport.getInspectorTabJarTargets(process)
        } catch (e: AppInspectionProcessNoLongerExistsException) {
          // Process died before we got a chance to connect, so we won't be launching any inspector
          // tabs this time.
          emptyList()
        }

      val tabs =
        tabTargetsList.map { tabTargets ->
          withContext(uiDispatcher) { AppInspectorTabShell(tabTargets) }.also { shell ->
            launchInspectorForTab(process, shell, force)
          }
        }

      withContext(uiDispatcher) {
        inspectorTabs.clear()
        tabs.sorted().forEach { tab -> inspectorTabs.add(tab) }
        updateUi()
        fireTabsChangedListener()
      }
    }
  }

  private fun fireTabsChangedListener() {
    tabsChangedListener?.invoke()
  }

  private fun createInspectorTabsPane(): CommonTabbedPane {
    // Active inspectors are sorted to the front, so make sure one of them gets default focus
    // Use same text colors for both active and inactive tabs, which is consistent with AS
    // components.
    val inspectorTabsPane =
      CommonTabbedPane(
        CommonTabbedPaneUI(CommonTabbedPaneUI.TEXT_COLOR, CommonTabbedPaneUI.TEXT_COLOR)
      )
    inspectorTabs.forEach { tab -> tab.addTo(inspectorTabsPane) }
    // Set the selected tab to the previous tab that was selected if possible. Otherwise, default to
    // the first one.
    inspectorTabsPane.selectedIndex =
      if (inspectorTabs.size > 0 && this::selectedTabName.isInitialized) {
        inspectorTabs.indexOfFirst { it.provider.displayName == selectedTabName }.takeIf { it >= 0 }
          ?: 0
      } else 0
    // Add after selection has been set to avoid setting off the listener prematurely.
    inspectorTabsPane.addChangeListener { event ->
      if (currentProcess?.isRunning == true) {
        (event.source as? CommonTabbedPane)?.let { tabbedPane ->
          if (tabbedPane.selectedIndex >= 0) {
            selectedTabName = tabbedPane.getTitleAt(tabbedPane.selectedIndex)
          }
        }
      }
    }
    return inspectorTabsPane
  }

  @UiThread
  private fun updateUi() {
    inspectorPanel.removeAll()
    val inspectorComponent =
      if (inspectorTabs.size > 0) createInspectorTabsPane() else noInspectorsMessage
    inspectorPanel.add(inspectorComponent)
  }

  /** Handles the termination and exceptions to tabs provided by [SingleAppInspectorTabProvider]. */
  private suspend fun waitAndHandleSingleInspectorTermination(
    target: AppInspectorMessengerTarget.Resolved,
    provider: AppInspectorTabProvider,
    tabShell: AppInspectorTabShell
  ) {
    val process = currentProcess!!
    when (target.messenger.awaitForDisposal()) {
      is AppInspectorForcefullyDisposedException -> {
        withContext(uiDispatcher) {
          tabShell.setComponent(
            EmptyStatePanel(
              AppInspectionBundle.message("inspector.forcefully.stopped", provider.displayName),
              provider.learnMoreUrl
            )
          )
        }
      }
      is AppInspectionCrashException -> {
        AppInspectionAnalyticsTrackerService.getInstance(project)
          .trackErrorOccurred(AppInspectionEvent.ErrorKind.INSPECTOR_CRASHED)
        // Wait until AFTER we're disposed before showing the notification. This ensures if
        // the user hits restart, which requests launching a new inspector, it won't reuse
        // the existing client. (Users probably would never hit restart fast enough but it's
        // possible to trigger in tests.)
        withContext(uiDispatcher) { showCrashNotification(provider.displayName, process, tabShell) }
      }
      else -> {
        withContext(uiDispatcher) {
          tabShell.setComponent(
            EmptyStatePanel(
              AppInspectionBundle.message("inspector.stopped", provider.displayName),
              provider.learnMoreUrl
            )
          )
        }
      }
    }
  }

  private suspend fun waitAndHandleInspectorTermination(
    messengers: List<AppInspectorMessengerTarget>,
    provider: AppInspectorTabProvider,
    tabShell: AppInspectorTabShell
  ) {
    messengers.filterIsInstance(AppInspectorMessengerTarget.Resolved::class.java).forEach { target
      ->
      target.messenger.awaitForDisposal()
    }

    withContext(uiDispatcher) {
      tabShell.setComponent(
        EmptyStatePanel(
          AppInspectionBundle.message("inspector.stopped", provider.displayName),
          provider.learnMoreUrl
        )
      )
    }
  }

  private fun refreshCoroutineScope() {
    if (this::tabsLaunchScope.isInitialized) {
      tabsLaunchScope.cancel()
    }
    tabsLaunchScope = scope.createChildScope(true)
  }

  internal fun isInspectionActive() = processesModel.selectedProcess?.isRunning ?: false

  override fun dispose() {
    if (this::tabsLaunchScope.isInitialized) {
      tabsLaunchScope.cancel()
    }
    inspectorTabs.forEach { Disposer.dispose(it) }
  }

  fun isTabSelected(inspectorId: String): Boolean {
    val inspectorTabIndex =
      inspectorTabs
        .indexOfFirst { tab -> tab.provider.launchConfigs.find { it.id == inspectorId } != null }
        .takeUnless { it == -1 }
        ?: return false
    val pane = inspectorPanel.getComponent(0) as? CommonTabbedPane ?: return false
    return pane.selectedIndex == inspectorTabIndex
  }
}
