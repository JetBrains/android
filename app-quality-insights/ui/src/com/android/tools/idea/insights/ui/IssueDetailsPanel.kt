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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.filterReady
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TitledSeparator
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

private const val NOTHING_SELECTED_LABEL = "Select an issue."
private const val MAIN_CARD = "main"
private const val EMPTY_CARD = "empty"

/**
 * Returns a [Pair] of [Long] values representing the (start, end) times as millis since epoch.
 *
 * A [TimeIntervalFilter] represents a timeframe, from "now" to "X" time in the past. This extension
 * function calculates this interval as Milliseconds from the time it was called.
 */
private fun TimeIntervalFilter.asMillisFromNow(): Pair<Long, Long> {
  val endOfRange = Instant.now()
  val startOfRange = endOfRange.minus(numDays, ChronoUnit.DAYS)
  return startOfRange.toEpochMilli() to endOfRange.toEpochMilli()
}

class IssueDetailsPanel(
  private val controller: AppInsightsProjectLevelController,
  project: Project,
  val headerHeightUpdatedCallback: (Int) -> Unit,
  parentDisposable: Disposable,
  private val tracker: AppInsightsTracker,
  private val getConsoleUrl: (Connection, Pair<Long, Long>?, Set<Version>, IssueDetails) -> String
) : JPanel(BorderLayout()) {

  // The setting of this field has the side effect of updating the UI, so it should be done on the
  // EDT thread.
  private var selectedIssue: AppInsightsIssue? = null
    set(value) {
      assert(SwingUtilities.isEventDispatchThread())
      if (field != value) {
        (mainPanel.layout as CardLayout).show(
          mainPanel,
          if (value != null) MAIN_CARD else EMPTY_CARD
        )
        updateHeaderSection(value)
        field = value
      }
    }

  private var selectedFirebaseConnection: VariantConnection? = null

  private var selectedTimeIntervalAsSeconds: Pair<Long, Long>? = null

  private var selectedVersion: Set<Version> = emptySet()

  private var connectionMode = ConnectionMode.ONLINE

  // Title, user, event counts
  private val titleLabel = SimpleColoredComponent()
  private val eventsCountLabel = JLabel(StudioIcons.AppQualityInsights.ISSUE)
  private val usersCountLabel = JLabel(StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE)

  // Affected version, signals, open/close button
  private val affectedVersionsLabel = JLabel("Versions affected", SwingConstants.LEFT)
  private val signalsPanel =
    JPanel(FlowLayout(FlowLayout.LEADING)).apply {
      border = JBUI.Borders.empty(2, 8, 0, 0)
      isOpaque = false
    }

  private val selectedState =
    controller.state.map { it.issues.map { timed -> timed.value } }.distinctUntilChanged()

  private val toggleButtonStateFlow =
    selectedState
      .filterReady()
      .mapNotNull { it.selected }
      .distinctUntilChanged()
      .combine(
        controller.state
          .map { ToggleButtonEnabledState(it.permission, it.mode) }
          .distinctUntilChanged()
      ) { selection, isEnabled ->
        ToggleButtonState(selection, isEnabled)
      }
      .distinctUntilChanged()

  @VisibleForTesting
  val toggleButton =
    if (!StudioFlags.OPEN_CLOSE_ISSUES_ENABLED.get()) JButton().also { it.isVisible = false }
    else
      ToggleButton(
        withIssue = { block ->
          controller.coroutineScope.launch {
            toggleButtonStateFlow
              .onEach {
                toolTipText =
                  if (it.buttonState.permission != Permission.FULL)
                    "You don't have the necessary permissions to open/close issues."
                  else if (it.buttonState.mode == ConnectionMode.OFFLINE) "AQI is offline."
                  else null
              }
              .collect { block(it) }
          }
        },
        onOpen = controller::openIssue,
        onClose = controller::closeIssue
      )

  // Firebase Link
  private val firebaseConsoleHyperlink: HyperlinkLabel =
    HyperlinkLabel("View on Firebase").apply {
      isFocusable = true
      // Restricting the max size to preferred size is needed to keep the external link icon
      // adjacent to the link.
      maximumSize = preferredSize
    }

  // Device, Version, Timestamp information
  private val deviceLabel =
    JLabel("sample device", StudioIcons.LayoutEditor.Toolbar.DEVICE_SCREEN, SwingConstants.LEFT)
  private val versionLabel =
    JLabel("1.2.3", StudioIcons.LayoutEditor.Toolbar.ANDROID_API, SwingConstants.LEFT)
  private val timestampLabel =
    JLabel("yesterday", StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK, SwingConstants.LEFT)

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
    AppInsightsStatusText(mainPanel) { selectedIssue == null }
      .apply {
        appendText(NOTHING_SELECTED_LABEL, EMPTY_STATE_TITLE_FORMAT)
        appendSecondaryText(
          "Select an issue to view the stacktrace.",
          EMPTY_STATE_TEXT_FORMAT,
          null
        )
      }

  private val scrollPane: JScrollPane
  @VisibleForTesting val stackTraceConsole = StackTraceConsole(controller, project, tracker)

  init {
    minimumSize = Dimension(90, 0)
    background = primaryContentBackground
    Disposer.register(parentDisposable, stackTraceConsole)
    controller.coroutineScope.launch {
      controller.state.collect { state ->
        selectedFirebaseConnection = state.connections.selected
        connectionMode = state.mode
        // We do the conversion to millis here to ensure the range matches the time of the response,
        // rather than the time when the
        // link was clicked.
        selectedTimeIntervalAsSeconds = state.filters.timeInterval.selected?.asMillisFromNow()
        selectedVersion =
          with(state.filters.versions) {
            if (allSelected()) emptySet() else items.asSequence().map { it.value }.toSet()
          }
      }
    }
    controller.coroutineScope.launch {
      selectedState
        .onEach { selectedIssue = (it as? LoadingState.Ready)?.value?.selected }
        .filterReady()
        .collect { selection ->
          selection.selected?.let { issue ->
            deviceLabel.text =
              issue.sampleEvent.eventData.device.let { "${it.manufacturer} ${it.model}" }
            versionLabel.text = issue.sampleEvent.eventData.operatingSystemInfo.displayVersion
            timestampLabel.text = dateFormatter.format(issue.sampleEvent.eventData.eventTime)
            val firebaseConnection = selectedFirebaseConnection?.connection
            if (firebaseConnection != null) {
              firebaseConsoleHyperlink.setHyperlinkTarget(
                getConsoleUrl(
                  firebaseConnection,
                  selectedTimeIntervalAsSeconds,
                  selectedVersion,
                  issue.issueDetails
                )
              )
              firebaseConsoleHyperlink.addMouseListener(
                object : MouseAdapter() {
                  override fun mousePressed(e: MouseEvent?) {
                    tracker.logConsoleLinkClicked(
                      connectionMode,
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
              firebaseConsoleHyperlink.setHyperlinkTarget("https://console.firebase.com")
            }
            updateBodySection(issue)
          }
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
    val header = createHeaderSection()
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
          add(signalsPanel)
          add(Box.createHorizontalGlue())
          add(toggleButton)
        }
      )
      add(
        transparentPanel().apply {
          layout = BoxLayout(this, BoxLayout.X_AXIS)
          add(deviceLabel)
          add(Box.createHorizontalStrut(8))
          add(versionLabel)
          add(Box.createHorizontalStrut(8))
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
          add(firebaseConsoleHyperlink)
          add(Box.createHorizontalGlue())
        }
      )
    }

  private fun createHeaderSection() =
    JPanel(BorderLayout()).apply {
      add(titleLabel, BorderLayout.WEST)
      val wrapAction =
        object : AbstractToggleUseSoftWrapsAction(SoftWrapAppliancePlaces.CONSOLE, false) {
          init {
            ActionUtil.copyFrom(this, IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS)
          }
          override fun getEditor(e: AnActionEvent) = stackTraceConsole.consoleView.editor
        }
      val toolbar =
        ActionManager.getInstance()
          .createActionToolbar("StackTraceToolbar", DefaultActionGroup(wrapAction), true)
      toolbar.targetComponent = this
      toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      toolbar.setReservePlaceAutoPopupIcon(false)
      ActionToolbarUtil.makeToolbarNavigable(toolbar)
      toolbar.component.apply { isOpaque = false }
      add(toolbar.component, BorderLayout.EAST)
      border = JBUI.Borders.empty(0, 8)
      preferredSize = Dimension(0, JBUIScale.scale(28))
    }

  private fun updateHeaderSection(issue: AppInsightsIssue?) {
    titleLabel.clear()

    if (issue == null) return

    titleLabel.icon = issue.issueDetails.fatality.getIcon()
    val (className, methodName) = issue.issueDetails.getDisplayTitle()
    val style =
      when (issue.state) {
        IssueState.OPEN,
        IssueState.OPENING -> SimpleTextAttributes.STYLE_PLAIN
        IssueState.CLOSED,
        IssueState.CLOSING -> SimpleTextAttributes.STYLE_STRIKEOUT
      }
    titleLabel.append(className, SimpleTextAttributes(style, null))
    if (methodName.isNotEmpty()) {
      titleLabel.append(".", SimpleTextAttributes(style, null))
      titleLabel.append(
        methodName,
        SimpleTextAttributes(style or SimpleTextAttributes.STYLE_BOLD, null)
      )
    }
  }

  private fun updateBodySection(issue: AppInsightsIssue) {
    eventsCountLabel.icon = StudioIcons.AppQualityInsights.ISSUE
    eventsCountLabel.text = issue.issueDetails.eventsCount.ifZero("-")
    usersCountLabel.icon = StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE
    usersCountLabel.text = issue.issueDetails.impactedDevicesCount.ifZero("-")

    affectedVersionsLabel.text =
      "Versions affected: ${issue.issueDetails.firstSeenVersion} - ${issue.issueDetails.lastSeenVersion}"
    signalsPanel.removeAll()
    issue.issueDetails.signals.forEachIndexed { idx, signal ->
      signalsPanel.add(
        JLabel("$signal", signal.icon, SwingConstants.LEFT).apply {
          if (idx > 0) {
            border = JBUI.Borders.emptyLeft(8)
          }
        }
      )
    }
  }

  override fun updateUI() {
    super.updateUI()
    emptyText?.setFont(StartupUiUtil.getLabelFont())
  }
}
