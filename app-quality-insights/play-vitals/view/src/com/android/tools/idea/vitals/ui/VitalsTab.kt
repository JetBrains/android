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
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.ActionToolbarListenerForOfflineBalloon
import com.android.tools.idea.insights.ui.AppInsightsModuleSelector
import com.android.tools.idea.insights.ui.Timestamp
import com.android.tools.idea.insights.ui.actions.AppInsightsDisplayRefreshTimestampAction
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.android.tools.idea.insights.ui.actions.AppInsightsToggleAction
import com.android.tools.idea.insights.ui.actions.TreeDropDownAction
import com.android.tools.idea.insights.ui.offlineModeIcon
import com.android.tools.idea.insights.ui.toTimestamp
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val offlineAction =
  object : AnAction("Play Vitals is offline.", null, offlineModeIcon) {
    override fun actionPerformed(e: AnActionEvent) {}
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = false
      e.presentation.disabledIcon = offlineModeIcon
      super.update(e)
    }
  }

class VitalsTab(
  private val projectController: AppInsightsProjectLevelController,
  private val project: Project,
  private val clock: Clock
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

  private val timestamp: Flow<Timestamp> =
    projectController.state.toTimestamp(clock).distinctUntilChanged()

  private val offlineStateFlow = projectController.state.map { it.mode }.distinctUntilChanged()

  private val failureTypeToggles =
    projectController.state
      .map { state -> state.filters.failureTypeToggles.selected }
      .distinctUntilChanged()
  private val userPerceivedToggle =
    failureTypeToggles.map { it.contains(FailureType.USER_PERCEIVED_ONLY) }
  private val foregroundToggle = failureTypeToggles.map { it.contains(FailureType.FOREGROUND) }
  private val backgroundToggle = failureTypeToggles.map { it.contains(FailureType.BACKGROUND) }

  init {
    add(createToolbar().component, BorderLayout.NORTH)
    add(
      VitalsContentContainerPanel(
        projectController,
        project,
        project.service<AppInsightsTracker>(),
        this
      )
    )
  }

  private fun createToolbar(): ActionToolbar {
    val actionGroups =
      DefaultActionGroup().apply {
        add(
          AppInsightsModuleSelector(
            "Module Selector",
            null,
            null,
            connections,
            projectController::selectConnection
          )
        )
        addSeparator()
        add(
          AppInsightsToggleAction(
            "User-perceived",
            null,
            // TODO(b/271918057): update icon
            StudioIcons.AppQualityInsights.FATAL,
            userPerceivedToggle,
            scope
          ) {
            projectController.toggleFailureType(FailureType.USER_PERCEIVED_ONLY)
          }
        )
        add(
          AppInsightsToggleAction(
            "Foreground",
            null,
            // TODO(b/271918057): update icon
            StudioIcons.AppQualityInsights.NON_FATAL,
            foregroundToggle,
            scope
          ) {
            projectController.toggleFailureType(FailureType.FOREGROUND)
          }
        )
        add(
          AppInsightsToggleAction(
            "Background",
            null,
            // TODO(b/271918057): update icon
            StudioIcons.AppQualityInsights.ANR,
            backgroundToggle,
            scope
          ) {
            projectController.toggleFailureType(FailureType.BACKGROUND)
          }
        )
        addSeparator()
        add(
          AppInsightsDropDownAction(
            "Interval",
            null,
            null,
            intervals,
            null,
            projectController::selectTimeInterval
          )
        )
        add(
          TreeDropDownAction(
            name = "versions",
            flow = versions,
            scope = scope,
            groupNameSupplier = { it.displayVersion },
            nameSupplier = { it.buildVersion },
            secondaryGroupSupplier = { it.tracks },
            onSelected = projectController::selectVersions,
            secondaryTitleSupplier = {
              JLabel(StudioIcons.Avd.DEVICE_PLAY_STORE).apply {
                text = "Play Tracks"
                horizontalAlignment = SwingConstants.LEFT
              }
            }
          )
        )
        add(
          TreeDropDownAction(
            name = "devices",
            flow = devices,
            scope = scope,
            groupNameSupplier = { it.manufacturer },
            nameSupplier = { it.model },
            onSelected = projectController::selectDevices
          )
        )
        add(
          TreeDropDownAction(
            name = "operating systems",
            flow = operatingSystems,
            scope = scope,
            groupNameSupplier = { it.displayName },
            nameSupplier = { it.displayName },
            onSelected = projectController::selectOperatingSystems
          )
        )
        addSeparator()
        add(offlineAction)
        add(
          object : AnAction("Refresh", null, StudioIcons.LayoutEditor.Toolbar.REFRESH) {
            private val offlineState =
              offlineStateFlow.stateIn(scope, SharingStarted.Eagerly, ConnectionMode.ONLINE)
            override fun actionPerformed(e: AnActionEvent) {
              // TODO
            }
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun displayTextInToolbar() = true
            override fun update(e: AnActionEvent) {
              e.presentation.text =
                if (offlineState.value == ConnectionMode.OFFLINE) "Reconnect" else null
            }
          }
        )
        add(AppInsightsDisplayRefreshTimestampAction(timestamp, clock, scope))
      }
    val actionToolbar =
      ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
        targetComponent = this@VitalsTab
      }
    ActionToolbarUtil.findActionButton(actionToolbar, offlineAction)?.isVisible = false
    actionToolbar.component.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    actionToolbar.component.addContainerListener(
      ActionToolbarListenerForOfflineBalloon(
        "Play Vitals",
        project,
        offlineAction,
        scope,
        offlineStateFlow
      )
    )
    return actionToolbar
  }

  override fun dispose() = Unit
}
