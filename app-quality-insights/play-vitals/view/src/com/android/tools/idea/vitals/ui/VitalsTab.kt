/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.insights.selectionOf
import com.android.tools.idea.insights.ui.OfflineBalloonMaker
import com.android.tools.idea.insights.ui.Timestamp
import com.android.tools.idea.insights.ui.actions.AppInsightsDisplayRefreshTimestampAction
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.android.tools.idea.insights.ui.actions.AppInsightsToggleAction
import com.android.tools.idea.insights.ui.actions.TreeDropDownAction
import com.android.tools.idea.insights.ui.toTimestamp
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.intellij.collaboration.async.mapState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.time.Clock
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VitalsTab(
  private val projectController: AppInsightsProjectLevelController,
  private val project: Project,
  clock: Clock,
  tracker: AppInsightsTracker,
) : JPanel(BorderLayout()), Disposable {
  private val scope = AndroidCoroutineScope(this)

  private val connections =
    projectController.state
      .map { it.connections }
      .stateIn(scope, SharingStarted.Eagerly, Selection.emptySelection())
  private val versions =
    projectController.state.map { state -> state.filters.versions }.distinctUntilChanged()
  private val devices =
    projectController.state.map { state -> state.filters.devices }.distinctUntilChanged()
  private val operatingSystems =
    projectController.state.map { state -> state.filters.operatingSystems }.distinctUntilChanged()
  private val intervals =
    projectController.state
      .map { state -> state.filters.timeInterval }
      .stateIn(scope, SharingStarted.Eagerly, Selection.emptySelection())
  private val visibilityTypes =
    projectController.state
      .map { state -> state.filters.visibilityType }
      .stateIn(scope, SharingStarted.Eagerly, selectionOf(VisibilityType.ALL))

  private val timestamp: Flow<Timestamp> =
    projectController.state.toTimestamp(clock).distinctUntilChanged()

  private val offlineStateFlow =
    projectController.state
      .map { it.mode }
      .stateIn(scope, SharingStarted.Eagerly, ConnectionMode.ONLINE)

  private val timestampAction = AppInsightsDisplayRefreshTimestampAction(timestamp, clock, scope)

  private val failureTypeFlow =
    projectController.state
      .map { state -> state.filters.failureTypeToggles.selected }
      .distinctUntilChanged()
  private val crashToggle = failureTypeFlow.map { it.contains(FailureType.FATAL) }
  private val anrToggle = failureTypeFlow.map { it.contains(FailureType.ANR) }

  init {
    add(createToolbar().component, BorderLayout.NORTH)
    add(VitalsContentContainerPanel(projectController, project, tracker, this))
  }

  private fun addActionsToGroup(group: DefaultActionGroup) {
    group.apply {
      @Suppress("UNCHECKED_CAST")
      add(
        VitalsConnectionSelectorAction(
          connections as StateFlow<Selection<VitalsConnection>>,
          scope,
          projectController::selectConnection,
        )
      )
      addSeparator()
      if (StudioFlags.CRASHLYTICS_J_UI.get()) {
        add(
          AppInsightsToggleAction(
            "Crash",
            null,
            StudioIcons.AppQualityInsights.FATAL,
            crashToggle,
            scope,
          ) {
            projectController.toggleFailureType(FailureType.FATAL)
          }
        )
        add(
          AppInsightsToggleAction(
            "ANR",
            null,
            StudioIcons.AppQualityInsights.ANR,
            anrToggle,
            scope,
          ) {
            projectController.toggleFailureType(FailureType.ANR)
          }
        )
      }
      add(
        AppInsightsDropDownAction(
          "Interval",
          null,
          null,
          intervals,
          null,
          projectController::selectTimeInterval,
        )
      )
      add(
        AppInsightsDropDownAction(
          "Visibility types",
          null,
          null,
          visibilityTypes,
          null,
          projectController::selectVisibilityType,
        )
      )
      add(
        TreeDropDownAction(
          name = "versions",
          flow = versions,
          scope = scope,
          enabledFlow = offlineStateFlow.mapState { it == ConnectionMode.ONLINE },
          groupNameSupplier = { it.displayVersion },
          nameSupplier = { it.buildVersion },
          secondaryGroupSupplier = { it.tracks },
          onSelected = projectController::selectVersions,
          secondaryTitleSupplier = {
            JLabel(StudioIcons.Avd.DEVICE_PLAY_STORE).apply {
              text = "Play Tracks"
              horizontalAlignment = SwingConstants.LEFT
            }
          },
        )
      )
      add(
        TreeDropDownAction(
          name = "devices",
          flow = devices,
          scope = scope,
          enabledFlow = offlineStateFlow.mapState { it == ConnectionMode.ONLINE },
          groupNameSupplier = { it.manufacturer },
          nameSupplier = { it.displayName },
          secondaryGroupSupplier = { setOf(it.deviceType) },
          onSelected = projectController::selectDevices,
          secondaryTitleSupplier = {
            JLabel().apply {
              text = "Device Types"
              horizontalAlignment = SwingConstants.LEFT
            }
          },
        )
      )
      add(
        TreeDropDownAction(
          name = "operating systems",
          flow = operatingSystems,
          scope = scope,
          enabledFlow = offlineStateFlow.mapState { it == ConnectionMode.ONLINE },
          groupNameSupplier = { it.displayName },
          nameSupplier = { it.displayName },
          onSelected = projectController::selectOperatingSystems,
        )
      )
      addSeparator()
      add(
        object : AnAction("Refresh", null, StudioIcons.LayoutEditor.Toolbar.REFRESH) {
          override fun actionPerformed(e: AnActionEvent) {
            projectController.refresh()
          }

          override fun getActionUpdateThread() = ActionUpdateThread.BGT

          override fun displayTextInToolbar() = true

          override fun update(e: AnActionEvent) {
            e.presentation.text =
              if (offlineStateFlow.value == ConnectionMode.OFFLINE) "Reconnect" else null
          }
        }
      )
      add(timestampAction)
    }
  }

  private fun createToolbar(): ActionToolbar {
    val group = DefaultActionGroup()
    val actionToolbar =
      ActionManager.getInstance().createActionToolbar("AppInsights", group, true).apply {
        targetComponent = this@VitalsTab
      }
    actionToolbar.component.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    scope.launch(AndroidDispatchers.uiThread) {
      offlineStateFlow.collect { mode ->
        ActionToolbarUtil.findActionButton(actionToolbar, timestampAction)?.let { button ->
          if (
            mode == ConnectionMode.OFFLINE &&
              !project.service<AppInsightsSettings>().isOfflineNotificationDismissed
          ) {
            OfflineBalloonMaker("Android Vitals", project).showOfflineNotificationBalloon(button)
          }
        }
      }
    }
    addActionsToGroup(group)
    return actionToolbar
  }

  override fun dispose() = Unit
}
