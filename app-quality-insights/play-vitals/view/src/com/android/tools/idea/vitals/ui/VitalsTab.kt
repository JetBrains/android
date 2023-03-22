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
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.Timestamp
import com.android.tools.idea.insights.ui.TimestampState
import com.android.tools.idea.insights.ui.actions.AppInsightsDisplayRefreshTimestampAction
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.android.tools.idea.insights.ui.actions.AppInsightsToggleAction
import com.android.tools.idea.insights.ui.actions.TreeDropDownAction
import com.android.tools.idea.insights.ui.offlineModeIcon
import com.android.tools.idea.vitals.client.VitalsConnection
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.time.Clock
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

class VitalsTab(configurationManager: AppInsightsConfigurationManager, private val clock: Clock) :
  JPanel(BorderLayout()), Disposable {
  val scope = AndroidCoroutineScope(this)

  // TODO(b/271919516): remove once the vitals client is ready.
  private val fakeIntervalsFlow =
    flowOf(Selection(TimeIntervalFilter.TWENTY_EIGHT_DAYS, VitalsTimeIntervals))
      .stateIn(scope, SharingStarted.Eagerly, Selection.emptySelection())

  private val fakeVersionsFlow =
    flowOf(MultiSelection(emptySet(), listOf(WithCount(10, Version("1", "1.0")))))
  private val fakeOfflineStateFlow = flowOf(ConnectionMode.ONLINE)
  private val fakeDevicesFlow =
    flowOf(
      MultiSelection(
        emptySet(),
        listOf(WithCount(10, Device("Google", "Pixel 2")), WithCount(22, Device("Samsung", "S22")))
      )
    )

  private val fakeOsFlow =
    flowOf(
      MultiSelection(
        emptySet(),
        listOf(
          WithCount(10, OperatingSystemInfo("11", "Android (11)")),
          WithCount(11, OperatingSystemInfo("12", "Android (12)"))
        )
      )
    )

  private val fakeTimestampFlow = flowOf(Timestamp(clock.instant(), TimestampState.ONLINE))

  private val fakeConnection = VitalsConnection("com.android.fake.app")
  private val fakeConnections = flowOf(Selection(fakeConnection, listOf(fakeConnection)))

  init {
    add(createToolbar().component, BorderLayout.NORTH)
    add(
      VitalsContentContainerPanel(
        configurationManager,
        configurationManager.project.service<AppInsightsTracker>(),
        this
      )
    )
  }

  private fun createToolbar(): ActionToolbar {
    val actionGroups =
      DefaultActionGroup().apply {
        add(
          AppInsightsDropDownAction(
            "Connection Selector",
            null,
            null,
            fakeConnections.stateIn(scope, SharingStarted.Eagerly, Selection.emptySelection()),
            null
          ) {}
        )
        addSeparator()
        add(
          AppInsightsToggleAction(
            "User-perceived",
            null,
            StudioIcons.AppQualityInsights.FATAL,
            emptyFlow(),
            scope
          ) {}
        )
        add(
          AppInsightsToggleAction(
            "Foreground",
            null,
            StudioIcons.AppQualityInsights.NON_FATAL,
            emptyFlow(),
            scope
          ) {}
        )
        add(
          AppInsightsToggleAction(
            "Background",
            null,
            StudioIcons.AppQualityInsights.ANR,
            emptyFlow(),
            scope
          ) {}
        )
        addSeparator()
        add(AppInsightsDropDownAction("Interval", null, null, fakeIntervalsFlow, null) {})
        add(
          TreeDropDownAction(
            name = "versions",
            flow = fakeVersionsFlow,
            scope = scope,
            groupNameSupplier = { it.displayVersion },
            nameSupplier = { it.buildVersion },
            secondaryGroupSupplier = { it.tracks },
            onSelected = {},
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
            flow = fakeDevicesFlow,
            scope = scope,
            groupNameSupplier = { it.manufacturer },
            nameSupplier = { it.model },
            onSelected = {}
          )
        )
        add(
          TreeDropDownAction(
            name = "operating systems",
            flow = fakeOsFlow,
            scope = scope,
            groupNameSupplier = { it.displayName },
            nameSupplier = { it.displayName },
            onSelected = {}
          )
        )
        addSeparator()
        add(offlineAction)
        add(
          object : AnAction("Refresh", null, StudioIcons.LayoutEditor.Toolbar.REFRESH) {
            private val offlineState =
              fakeOfflineStateFlow.stateIn(scope, SharingStarted.Eagerly, ConnectionMode.ONLINE)
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
        add(AppInsightsDisplayRefreshTimestampAction(fakeTimestampFlow, clock, scope))
      }
    val actionToolbar =
      ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
        targetComponent = this@VitalsTab
      }
    ActionToolbarUtil.findActionButton(actionToolbar, offlineAction)?.isVisible = false
    actionToolbar.component.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    // TODO: add offline balloon
    return actionToolbar
  }

  override fun dispose() = Unit
}
