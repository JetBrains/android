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

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.AppInsightsStatusText
import com.android.tools.idea.insights.ui.DetailsPanelHeader
import com.android.tools.idea.insights.ui.EMPTY_STATE_TEXT_FORMAT
import com.android.tools.idea.insights.ui.EMPTY_STATE_TITLE_FORMAT
import com.android.tools.idea.insights.ui.StackTraceConsole
import com.android.tools.idea.insights.ui.dateFormatter
import com.android.tools.idea.insights.ui.ifZero
import com.android.tools.idea.insights.ui.prettyRangeString
import com.android.tools.idea.insights.ui.transparentPanel
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.BoxLayout.Y_AXIS
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

private const val NOTHING_SELECTED_LABEL = "Select an issue."
private const val MAIN_CARD = "main"
private const val EMPTY_CARD = "empty"

data class VitalsDetailsState(
  val selectedConnection: Connection?,
  val selectedTimeInterval: TimeIntervalFilter?,
  val selectedVersion: Set<Version>,
  val selectedIssue: AppInsightsIssue?,
  val connectionMode: ConnectionMode,
  val selectedOsVersion: Set<OperatingSystemInfo>,
  val selectedDevices: Set<Device>,
  val selectedVisibility: VisibilityType?
) {

  fun toConsoleUrl(): String? {
    if (selectedIssue == null) {
      // Can't generate a link to an issue if none are selected.
      return null
    }

    val params = mutableListOf<String>()
    if (selectedTimeInterval != null) {
      params.add("days=${selectedTimeInterval.numDays}")
    }
    if (selectedVersion.isNotEmpty()) {
      params.add("versionCode=${selectedVersion.joinToString(",") { it.buildVersion }}")
    }
    if (selectedOsVersion.isNotEmpty()) {
      params.add("osVersion=${selectedOsVersion.joinToString(",") { it.displayVersion }}")
    }
    if (selectedDevices.isNotEmpty()) {
      params.add("deviceName=${selectedDevices.joinToString(",") { it.model }}")
    }
    if (selectedVisibility != null && selectedVisibility != VisibilityType.ALL) {
      params.add(
        // TODO(b/280341834): use isUserPerceived filter when it's available in the API.
        when (selectedVisibility) {
          VisibilityType.USER_PERCEIVED -> "appProcessState=Foreground"
          else -> "" // Shouldn't hit this case.
        }
      )
    }
    return "${selectedIssue.issueDetails.uri}?${params.joinToString("&")}"
  }
}

private val DefaultVitalsDetailsState =
  VitalsDetailsState(
    null,
    null,
    emptySet(),
    null,
    ConnectionMode.ONLINE,
    emptySet(),
    emptySet(),
    null
  )

class VitalsIssueDetailsPanel(
  controller: AppInsightsProjectLevelController,
  project: Project,
  val headerHeightUpdatedCallback: (Int) -> Unit,
  parentDisposable: Disposable,
  private val tracker: AppInsightsTracker
) : JPanel(BorderLayout()) {
  private val scope = AndroidCoroutineScope(parentDisposable)
  private val detailsState =
    controller.state
      .map { state ->
        VitalsDetailsState(
          state.connections.selected,
          state.filters.timeInterval.selected,
          state.filters.versions.getSelectedValueOrEmpty(),
          state.selectedIssue,
          state.mode,
          state.filters.operatingSystems.getSelectedValueOrEmpty(),
          state.filters.devices.getSelectedValueOrEmpty(),
          state.filters.visibilityType.selected
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, DefaultVitalsDetailsState)

  @VisibleForTesting val stackTraceConsole = StackTraceConsole(controller, project, tracker)

  // Title
  private val header = DetailsPanelHeader(stackTraceConsole.consoleView.editor)

  // TODO(b/290647605): add back device label
  // Events, users, affected api levels
  private val eventsCountLabel = JLabel(StudioIcons.AppQualityInsights.ISSUE)
  private val usersCountLabel = JLabel(StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE)
  private val affectedApiLevelsLabel = JLabel(StudioIcons.LayoutEditor.Toolbar.ANDROID_API)

  // Affected app version
  private val affectedVersionsLabel = JLabel("Versions affected", SwingConstants.LEFT)

  // Timestamp, Vitals Link
  private val timestampLabel =
    JLabel("yesterday", StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK, SwingConstants.LEFT)
  private val vitalsConsoleLink: HyperlinkLabel =
    HyperlinkLabel("View on Android Vitals").apply {
      isFocusable = true
      // Restricting the max size to preferred size is needed to keep the external link icon
      // adjacent to the link.
      maximumSize = preferredSize
    }

  // Sdk insights
  private val insightsPanel = transparentPanel().apply { layout = BoxLayout(this, Y_AXIS) }

  private val mainPanel: JPanel =
    object : JPanel(CardLayout()) {
      init {
        isOpaque = false
      }

      override fun paint(g: Graphics?) {
        super.paint(g)
        emptyText.paint(this, g)
      }
    }

  @VisibleForTesting
  val emptyText =
    AppInsightsStatusText(mainPanel) { detailsState.value.selectedIssue == null }
      .apply {
        appendText(NOTHING_SELECTED_LABEL, EMPTY_STATE_TITLE_FORMAT)
        appendSecondaryText(
          "Select an issue to view the stacktrace.",
          EMPTY_STATE_TEXT_FORMAT,
          null
        )
      }

  private val scrollPane: JScrollPane

  init {
    minimumSize = Dimension(90, 0)
    background = primaryContentBackground
    Disposer.register(parentDisposable, stackTraceConsole)
    scope.launch(AndroidDispatchers.uiThread) {
      detailsState
        .map { it.selectedIssue }
        .distinctUntilChanged()
        .collect { issue ->
          (mainPanel.layout as CardLayout).show(
            mainPanel,
            if (issue != null) MAIN_CARD else EMPTY_CARD
          )
          header.updateWithIssue(issue)
        }
    }

    scope.launch(AndroidDispatchers.uiThread) {
      detailsState
        .filter { it.selectedIssue != null }
        .collect { state ->
          val issue = state.selectedIssue!!
          if (state.selectedConnection != null) {
            vitalsConsoleLink.setHyperlinkTarget(state.toConsoleUrl())
            vitalsConsoleLink.addMouseListener(
              object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                  tracker.logConsoleLinkClicked(
                    state.connectionMode,
                    AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails.newBuilder()
                      .apply {
                        source =
                          AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails
                            .ConsoleOpenSource
                            .DETAILS
                        crashType = issue.issueDetails.fatality.toCrashType()
                      }
                      .build()
                  )
                }
              }
            )
          } else {
            vitalsConsoleLink.setHyperlinkTarget("https://play.google.com/console")
          }
          updateBodySection(issue)
        }
    }

    scrollPane =
      ScrollPaneFactory.createScrollPane(
          createContentPanel(),
          ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        .apply {
          isOpaque = false
          viewport.isOpaque = false
        }
    scrollPane.border = IdeBorderFactory.createBorder(SideBorder.NONE)
    stackTraceConsole.onStackPrintedListener = { scrollPane.verticalScrollBar.value = 0 }
    header.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          headerHeightUpdatedCallback(header.height)
        }
      }
    )
    add(header, BorderLayout.NORTH)
    mainPanel.add(scrollPane, MAIN_CARD)
    mainPanel.add(transparentPanel(), EMPTY_CARD)
    (mainPanel.layout as CardLayout).show(mainPanel, EMPTY_CARD)
    add(mainPanel, BorderLayout.CENTER)
  }

  private fun createContentPanel(): JComponent {
    val panel =
      transparentPanel().apply {
        border = JBUI.Borders.empty(0, 8)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(Box.createVerticalStrut(5))
        add(createBodySection())
        add(Box.createVerticalStrut(5))
        add(TitledSeparator("Stack Trace"))
        add(stackTraceConsole.consoleView.component)
        components.forEach { (it as JComponent).alignmentX = LEFT_ALIGNMENT }
      }
    return transparentPanel(BorderLayout()).apply { add(panel, BorderLayout.NORTH) }
  }

  private fun createBodySection() =
    transparentPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(
        transparentPanel().apply {
          layout = BoxLayout(this, BoxLayout.X_AXIS)
          add(eventsCountLabel)
          add(Box.createHorizontalStrut(8))
          add(usersCountLabel)
          add(Box.createHorizontalStrut(8))
          add(affectedApiLevelsLabel)
          add(Box.createHorizontalGlue())
        }
      )
      add(Box.createVerticalStrut(5))
      add(
        transparentPanel().apply {
          layout = BoxLayout(this, BoxLayout.X_AXIS)
          add(affectedVersionsLabel)
          add(Box.createHorizontalGlue())
        }
      )
      add(Box.createVerticalStrut(5))
      add(
        transparentPanel().apply {
          layout = BoxLayout(this, BoxLayout.X_AXIS)
          add(timestampLabel)
          add(Box.createHorizontalStrut(8))
          add(vitalsConsoleLink)
          add(Box.createHorizontalGlue())
        }
      )
      add(Box.createVerticalStrut(5))
      add(add(insightsPanel))
    }

  private fun updateBodySection(issue: AppInsightsIssue) {
    affectedApiLevelsLabel.text =
      prettyRangeString(
        issue.issueDetails.lowestAffectedApiLevel,
        issue.issueDetails.highestAffectedApiLevel
      )
    timestampLabel.text = dateFormatter.format(issue.sampleEvent.eventData.eventTime)

    eventsCountLabel.icon = StudioIcons.AppQualityInsights.ISSUE
    eventsCountLabel.text = issue.issueDetails.eventsCount.ifZero("-")
    usersCountLabel.icon = StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE
    usersCountLabel.text = issue.issueDetails.impactedDevicesCount.ifZero("-")

    affectedVersionsLabel.text =
      "Versions affected: ${prettyRangeString(issue.issueDetails.firstSeenVersion, issue.issueDetails.lastSeenVersion)}"

    insightsPanel.removeAll()
    issue.issueDetails.annotations.forEach {
      insightsPanel.add(SdkInsightsPanel(it.category, it.title, it.body))
      insightsPanel.add(Box.createVerticalStrut(5))
    }
  }

  override fun updateUI() {
    super.updateUI()
    emptyText?.setFont(StartupUiUtil.getLabelFont())
  }
}

private fun <T> MultiSelection<WithCount<T>>.getSelectedValueOrEmpty() =
  if (allSelected()) emptySet() else selected.map { it.value }.toSet()
