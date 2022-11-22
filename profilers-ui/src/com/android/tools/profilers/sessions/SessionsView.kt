/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.adtui.model.stdui.CommonAction.SeparatorAction
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.StandardColors
import com.android.tools.adtui.stdui.menu.CommonDropDownButton
import com.android.tools.idea.IdeInfo
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.ProfilerLayout
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.cpu.CpuCaptureArtifactView
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.memory.AllocationArtifactView
import com.android.tools.profilers.memory.AllocationSessionArtifact
import com.android.tools.profilers.memory.HeapProfdArtifactView
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.HprofArtifactView
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.LegacyAllocationsArtifactView
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.sessions.SessionArtifactView.ArtifactDrawInfo
import com.android.utils.usLocaleCapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Ordering
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.io.File
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * A collapsible panel which lets users see the list of and interact with their profiling sessions.
 */
class SessionsView(val profilers: StudioProfilers, val ideProfilerComponents: IdeProfilerComponents) : AspectObserver() {
  private val sessionsManager = profilers.sessionsManager
  val component: JComponent = JPanel(BorderLayout()).apply { border = AdtUiUtils.DEFAULT_RIGHT_BORDER }

  @VisibleForTesting val expandButton: JButton =
    collapseButton(StudioIcons.Profiler.Toolbar.EXPAND_SESSION, "Expand the Sessions panel.", false)
  @VisibleForTesting val collapseButton: JButton =
    collapseButton(StudioIcons.Profiler.Toolbar.COLLAPSE_SESSION, "Collapse the Sessions panel.", true)
  @VisibleForTesting val stopProfilingButton =
    toolbarButton(StudioIcons.Profiler.Toolbar.STOP_SESSION, "Stop the current profiling session.").apply {
      disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_SESSION)
      isEnabled = false
      addActionListener {
        if (confirm(HIDE_STOP_PROMPT, CONFIRM_END_TITLE, CONFIRM_END_MESSAGE)) stopProfilingSession()
      }
    }
  @VisibleForTesting val processSelectionAction = CommonAction("", AllIcons.General.Add).apply {
    setAction { profilers.ideServices.featureTracker.trackSessionDropdownClicked() }
  }

  private val processSelectionDropDown = CommonDropDownButton(processSelectionAction).apply {
    setUpToolbarButton("Start a new profiling session.")
  }

  // Sessions artifacts are vertically stacked in a single column.
  // We are using a scrollable JPanel instead of an JList because JList's cell renderer are not designed to support the animation and
  // interaction we want to support within each list item (e.g. spinning icons, nested buttons, etc)
  val sessionsPanel = JPanel().apply {
    layout = TabularLayout("*")
    isOpaque = false
  }

  val scrollPane = JBScrollPane(sessionsPanel).apply {
    viewport.isOpaque = false
    isOpaque = false
    border = BorderFactory.createEmptyBorder()
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
  }

  private var sessionArtifactViewBinder = ViewBinder<ArtifactDrawInfo, SessionArtifact<*>, SessionArtifactView<*>>().apply {
    bind(SessionItem::class.java, ::SessionItemView)
    bind(HprofSessionArtifact::class.java, ::HprofArtifactView)
    bind(HeapProfdSessionArtifact::class.java, ::HeapProfdArtifactView)
    bind(LegacyAllocationsSessionArtifact::class.java, ::LegacyAllocationsArtifactView)
    bind(CpuCaptureSessionArtifact::class.java, ::CpuCaptureArtifactView)
    bind(AllocationSessionArtifact::class.java, ::AllocationArtifactView)
  }

  @VisibleForTesting val collapsed get() = expandButton.isVisible

  init {
    profilers.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES) { refreshProcessDropdown() }
    sessionsManager.addDependency(this)
      .onChange(SessionAspect.SESSIONS) { refreshSessions() }
      .onChange(SessionAspect.PROFILING_SESSION) {
        stopProfilingButton.isEnabled = Common.Session.getDefaultInstance() != sessionsManager.profilingSession
      }
    initializeUI(profilers.ideServices.persistentProfilerPreferences.getBoolean(SESSION_IS_COLLAPSED, false))
    refreshProcessDropdown()
  }

  /**
   * @param listener Listener for when the sessions panel is expanded.
   */
  fun addExpandListener(listener: ActionListener) = expandButton.addActionListener(listener)

  /**
   * @param listener Listener for when the sessions panel is collapsed.
   */
  fun addCollapseListener(listener: ActionListener) = collapseButton.addActionListener(listener)

  private fun initializeUI(collapsed: Boolean) = with (component) {
    removeAll()
    if (collapsed) {
      // We only need the toolbar when collapsed
      add(collapsedToolbar(), BorderLayout.CENTER)
    } else {
      add(expandedToolbar(), BorderLayout.NORTH)
      add(scrollPane, BorderLayout.CENTER)
    }
    revalidate()
    repaint()
  }

  private fun collapsedToolbar() = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    minimumSize = Dimension(sessionsCollapsedMinWidth, 0)
    add(expandButton.apply { isVisible = true })
    // Note - if we simply remove the collapse button after it is clicked, next time we add it back it will
    // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
    // render and update its state even though it is hidden.
    add(collapseButton.apply { isVisible = false }, TabularLayout.Constraint(1, 0, 1, 3))
    add(stopProfilingButton)
    add(processSelectionDropDown)
  }

  private fun expandedToolbar() = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    border = AdtUiUtils.DEFAULT_BOTTOM_BORDER
    minimumSize = Dimension(sessionsExpandedMinWidth, ProfilerLayout.TOOLBAR_HEIGHT)
    preferredSize = Dimension(sessionsExpandedMinWidth, ProfilerLayout.TOOLBAR_HEIGHT)
    add(JLabel("SESSIONS").apply {
      alignmentY = Component.CENTER_ALIGNMENT
      border = ProfilerLayout.TOOLBAR_LABEL_BORDER
      font = ProfilerFonts.SMALL_FONT
      foreground = StandardColors.TEXT_COLOR
    })
    add(Box.createHorizontalGlue())
    add(processSelectionDropDown)
    add(stopProfilingButton)
    add(collapseButton.apply { isVisible = true })
    // Note - if we simply remove the expand button after it is clicked, next time we add it back it will
    // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
    // render and update its state even though it is hidden.
    add(expandButton.apply { isVisible = false })
  }

  private fun refreshSessions() = with (sessionsPanel) {
    removeAll()
    sessionsManager.sessionArtifacts.forEachIndexed { i, item ->
      add(sessionArtifactViewBinder.build(ArtifactDrawInfo(this@SessionsView, i), item), TabularLayout.Constraint(i, 0))
    }
    revalidate()
    repaint()
  }

  fun stopProfilingSession() = with (profilers) {
    // We should not start auto-profiling other things if the user manually stops a session.
    autoProfilingEnabled = false
    // Unselect the device and process which stops the session. This also avoids them from appearing to be selected in the process
    // selection dropdown even after the session has stopped.
    setProcess(null, null)
    ideServices.featureTracker.trackStopSession()
  }

  private fun refreshProcessDropdown() {
    processSelectionAction.clear()

    // Add the dropdown action for loading from file
    processSelectionAction.addChildrenActions(importAction(), SeparatorAction())

    // Rebuild the action tree.
    val entries = profilers.deviceProcessMap.filterKeys { it.state == Common.Device.State.ONLINE }
    when {
      entries.isEmpty() -> processSelectionAction.addChildrenActions(disabledAction(NO_SUPPORTED_DEVICES))
      else -> entries.forEach { (device, allProcesses) ->
        val deviceAction = commonAction(StudioProfilers.buildDeviceName(device))
        processSelectionAction.addChildrenActions(deviceAction)
        val processes = allProcesses.filter { it.state == Common.Process.State.ALIVE }
        when {
          processes.isEmpty() -> deviceAction.addChildrenActions(
            disabledAction(device.unsupportedReason.ifEmpty { NO_DEBUGGABLE_OR_PROFILEABLE_PROCESSES }))
          else -> {
            val processAction = fun (postFix: (Common.Process) -> String) = fun(process: Common.Process) =
              commonAction("${process.name} (${process.pid})${postFix(process)}").apply {
                setAction {
                  // First warn and stop the currently profiling session if there is one.
                  if (SessionsManager.isSessionAlive(profilers.sessionsManager.profilingSession)) {
                    // Do not continue to start a new session.
                    if (!confirm(HIDE_RESTART_PROMPT, CONFIRM_END_TITLE, CONFIRM_RESTART_MESSAGE)) return@setAction
                    stopProfilingSession()
                  }
                  profilers.setProcess(device, process)
                  profilers.ideServices.featureTracker.trackCreateSession(Common.SessionMetaData.SessionType.FULL,
                                                                          SessionsManager.SessionCreationSource.MANUAL)
                }
              }
            val plainProcessAction = processAction {""}
            val annotatedProcessAction = processAction {
              when (profilers.getLiveProcessSupportLevel(it.pid)) {
                SupportLevel.PROFILEABLE -> " (profileable)"
                else -> " (debuggable)"
              }
            }

            val (preferredProcesses, otherProcesses) = profilers.preferredProcessName.let { preferredProcess ->
              processes.partition { preferredProcess != null && it.name.startsWith(preferredProcess) }
            }
            val order = Comparator.comparing(CommonAction::getText, Ordering.natural())

            // Add separate menu items for other debuggable processes and other profileable processes
            fun addOtherProcessesFlyout(tag: String, actions: List<CommonAction>) = when {
              actions.isNotEmpty() -> {
                val title = if (IdeInfo.isGameTool()) "${tag.usLocaleCapitalize()} processes" else "Other $tag processes"
                val otherProcessesFlyout = CommonAction(title, null)
                otherProcessesFlyout.addChildrenActions(actions)
                deviceAction.addChildrenActions(otherProcessesFlyout)
              }
              else -> {
                val title = if (IdeInfo.isGameTool()) "No $tag processes" else "No other $tag processes"
                deviceAction.addChildrenActions(disabledAction(title))
              }
            }

            val preferredProcessActions = preferredProcesses.map(annotatedProcessAction).sortedWith(order)
            if (preferredProcessActions.isNotEmpty()) deviceAction.addChildrenActions(preferredProcessActions)
            // Only add the separator if there are preferred processes added.
            if (otherProcesses.isNotEmpty() && deviceAction.childrenActionCount != 0) deviceAction.addChildrenActions(SeparatorAction())
            val (debuggables, profileables) = otherProcesses.partition {
              profilers.getLiveProcessSupportLevel(it.pid) == SupportLevel.DEBUGGABLE
            }
            addOtherProcessesFlyout("debuggable", debuggables.map(plainProcessAction).sortedWith(order))
            addOtherProcessesFlyout("profileable", profileables.map(plainProcessAction).sortedWith(order))
          }
        }
      }
    }
  }

  private fun collapseButton(icon: Icon, tooltip: String, collapse: Boolean) = toolbarButton(icon, tooltip).apply {
    addActionListener {
      initializeUI(collapse)
      profilers.ideServices.persistentProfilerPreferences.setBoolean(SESSION_IS_COLLAPSED, collapse)
    }
  }

  private fun toolbarButton(icon: Icon, tooltip: String) = CommonButton(icon).apply {
    setUpToolbarButton(tooltip)
  }

  private fun AbstractButton.setUpToolbarButton(tooltip: String) {
    alignmentX = Component.CENTER_ALIGNMENT
    alignmentY = Component.CENTER_ALIGNMENT
    border = ProfilerLayout.TOOLBAR_ICON_BORDER
    toolTipText = tooltip
  }

  private fun commonAction(text: String) = CommonAction(text, null)
  private fun disabledAction(text: String) = commonAction(text).apply { isEnabled = false }
  private fun importAction() = commonAction("Load from file...").apply {
    setAction {
      val supportedExtensions = listOf("trace", "pftrace", "perfetto-trace", "alloc", "hprof", "heapprofd")
      ideProfilerComponents.createImportDialog().open({ "Open" }, supportedExtensions) { file ->
        if (!profilers.sessionsManager.importSessionFromFile(File(file.path))) {
          ideProfilerComponents.createUiMessageHandler()
            .displayErrorMessage(component, "File Open Error", "Unknown file type: ${file.path}" )
        }
      }
    }
  }

  private fun confirm(prefKey: String, title: String, msg: String): Boolean =
    profilers.ideServices.temporaryProfilerPreferences.getBoolean(prefKey, false) ||
    ideProfilerComponents.createUiMessageHandler()
      .displayOkCancelMessage(title, msg, CONFIRM_BUTTON_TEXT, CANCEL_BUTTON_TEXT, null) {
        profilers.ideServices.temporaryProfilerPreferences.setBoolean(prefKey, it)
      }

  companion object {
    private const val HIDE_STOP_PROMPT = "session.hide.stop.prompt"
    private const val HIDE_RESTART_PROMPT = "session.hide.restart.prompt"
    private const val CONFIRM_END_TITLE = "End Session"
    private const val CONFIRM_END_MESSAGE = "Are you sure you want to end the current profiling session?"
    private const val CONFIRM_RESTART_MESSAGE =
      "Selecting a different process stops the current profiler session and starts a new one. Do you want to continue?"
    private const val CONFIRM_BUTTON_TEXT = "Yes"
    private const val CANCEL_BUTTON_TEXT = "Cancel"

    /**
     * Preference string for whether the sessions UI is collapsed (bool).
     */
    const val SESSION_IS_COLLAPSED = "SESSION_IS_COLLAPSED"

    /**
     * Preference string for the last known width (int) of the sessions UI when it was expanded.
     */
    const val SESSION_EXPANDED_WIDTH = "SESSION_EXPANDED_WIDTH"

    /**
     * String to display in the dropdown when no devices are detected.
     */
    @VisibleForTesting
    val NO_SUPPORTED_DEVICES = "No supported devices"

    /**
     * String to display in the dropdown when no debuggable processes are detected.
     */
    @VisibleForTesting
    val NO_DEBUGGABLE_OR_PROFILEABLE_PROCESSES = "No debuggable or profileable processes"

    // Collapsed width should essentially look like a toolbar.
    private val sessionsCollapsedMinWidth get() = JBUI.scale(32)
    private val sessionsExpandedMinWidth get() = JBUI.scale(200)

    @JvmStatic
    fun getComponentMinimizeSize(isExpanded: Boolean) =
      Dimension(if (isExpanded) sessionsExpandedMinWidth else sessionsCollapsedMinWidth, 0)
  }
}