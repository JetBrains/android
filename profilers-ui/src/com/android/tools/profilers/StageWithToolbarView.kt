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
package com.android.tools.profilers

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.CommonToggleButton
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.stdui.DefaultContextMenuItem
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * A wrapper for a [StageView] with an accompanying toolbar and context menu.
 */
class StageWithToolbarView(private val studioProfilers: StudioProfilers,
                           stageComponent: JPanel,
                           ideProfilerComponents: IdeProfilerComponents,
                           stageViewBuilder: Function<Stage<*>, StageView<*>>,
                           containerComponent: JComponent) : AspectObserver() {

  private val stageCenterCardLayout: CardLayout = CardLayout()
  private val stageCenterComponent: JPanel = JPanel(stageCenterCardLayout)
  private val stageLoadingPanel: LoadingPanel
  private lateinit var toolbar: JPanel
  private lateinit var stageNavigationToolbar: StageNavigationToolbar
  private lateinit var zoomToSelectionAction: DefaultContextMenuItem
  private lateinit var goLiveToolbar: JPanel
  private lateinit var customStageToolbar: JPanel

  @get:VisibleForTesting
  lateinit var timelineNavigationToolbar: JPanel
    private set

  @get:VisibleForTesting
  lateinit var zoomOutButton: CommonButton
    private set

  @get:VisibleForTesting
  lateinit var zoomInButton: CommonButton
    private set

  @get:VisibleForTesting
  lateinit var resetZoomButton: CommonButton
    private set

  @get:VisibleForTesting
  lateinit var zoomToSelectionButton: CommonButton
    private set

  @get:VisibleForTesting
  lateinit var goLiveButton: JToggleButton
    private set

  var stageView: StageView<*>? = null
    private set

  @get:VisibleForTesting
  val stageLoadingComponent: JComponent
    get() = stageLoadingPanel.component

  @get:VisibleForTesting
  val stageViewComponent: JComponent
    get() = stageView!!.component

  init {
    stageLoadingPanel = ideProfilerComponents.createLoadingPanel(0)
    stageLoadingPanel.setLoadingText("")
    stageLoadingPanel.component.background = ProfilerColors.DEFAULT_BACKGROUND

    initializeStageUi(containerComponent, stageComponent)

    studioProfilers.addDependency(this)
      .onChange(ProfilerAspect.STAGE) { updateStageView(stageViewBuilder, containerComponent) }
      .onChange(ProfilerAspect.AGENT) { toggleStageLayout() }
      .onChange(ProfilerAspect.PREFERRED_PROCESS) { toggleStageLayout() }
    updateStageView(stageViewBuilder, containerComponent)
    toggleStageLayout()
  }

  private fun initializeStageUi(containerComponent: JComponent, stageComponent: JPanel) {
    toolbar = JPanel(BorderLayout())
    toolbar.border = DEFAULT_BOTTOM_BORDER
    toolbar.preferredSize = Dimension(0, ProfilerLayout.TOOLBAR_HEIGHT)

    stageNavigationToolbar = StageNavigationToolbar(studioProfilers)
    if (!studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
      toolbar.add(stageNavigationToolbar, BorderLayout.WEST)
    }

    timelineNavigationToolbar = JPanel(ProfilerLayout.createToolbarLayout())
    toolbar.add(timelineNavigationToolbar, BorderLayout.EAST)
    timelineNavigationToolbar.border = JBEmptyBorder(0, 0, 0, 2)

    zoomOutButton = CommonButton(AllIcons.General.ZoomOut)
    zoomOutButton.disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomOut)
    zoomOutButton.addActionListener {
      stageView!!.stage.timeline.zoomOut()
      studioProfilers.ideServices.featureTracker.trackZoomOut()
    }
    val zoomOutAction = DefaultContextMenuItem.Builder(ZOOM_OUT).setContainerComponent(containerComponent).setActionRunnable {
      zoomOutButton.doClick(0)
    }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                     KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
      .build()
    zoomOutButton.toolTipText = zoomOutAction.defaultToolTipText
    timelineNavigationToolbar.add(zoomOutButton)

    zoomInButton = CommonButton(AllIcons.General.ZoomIn)
    zoomInButton.disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomIn)
    zoomInButton.addActionListener {
      stageView!!.stage.timeline.zoomIn()
      studioProfilers.ideServices.featureTracker.trackZoomIn()
    }
    val zoomInAction = DefaultContextMenuItem.Builder(ZOOM_IN).setContainerComponent(containerComponent)
      .setActionRunnable { zoomInButton.doClick(0) }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                     KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                     KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER)).build()
    zoomInButton.toolTipText = zoomInAction.defaultToolTipText
    timelineNavigationToolbar.add(zoomInButton)

    resetZoomButton = CommonButton(StudioIcons.Common.RESET_ZOOM)
    resetZoomButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM)
    resetZoomButton.addActionListener {
      stageView!!.stage.timeline.resetZoom()
      studioProfilers.ideServices.featureTracker.trackResetZoom()
    }
    val resetZoomAction = DefaultContextMenuItem.Builder("Reset zoom").setContainerComponent(containerComponent)
      .setActionRunnable { resetZoomButton.doClick(0) }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                     KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build()
    resetZoomButton.toolTipText = resetZoomAction.defaultToolTipText
    timelineNavigationToolbar.add(resetZoomButton)

    zoomToSelectionButton = CommonButton(StudioIcons.Common.ZOOM_SELECT)
    zoomToSelectionButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT)
    zoomToSelectionButton.addActionListener {
      stageView!!.stage.timeline.frameViewToRange(stageView!!.stage.timeline.selectionRange)
      studioProfilers.ideServices.featureTracker.trackZoomToSelection()
    }
    zoomToSelectionAction = DefaultContextMenuItem.Builder("Zoom to Selection")
      .setContainerComponent(containerComponent)
      .setActionRunnable { zoomToSelectionButton.doClick(0) }
      .setEnableBooleanSupplier { stageView != null && !stageView!!.stage.timeline.selectionRange.isEmpty }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0))
      .build()
    zoomToSelectionButton.toolTipText = zoomToSelectionAction.defaultToolTipText
    timelineNavigationToolbar.add(zoomToSelectionButton)

    goLiveToolbar = JPanel(ProfilerLayout.createToolbarLayout())
    goLiveToolbar.add(FlatSeparator())

    goLiveButton = CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE)
    goLiveButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE)
    goLiveButton.font = ProfilerFonts.H4_FONT
    goLiveButton.horizontalTextPosition = SwingConstants.LEFT
    goLiveButton.horizontalAlignment = SwingConstants.LEFT
    goLiveButton.border = JBEmptyBorder(3, 7, 3, 7)

    // Configure shortcuts for GoLive.
    val attachAction = DefaultContextMenuItem.Builder(ATTACH_LIVE).setContainerComponent(containerComponent)
      .setActionRunnable { goLiveButton.doClick(0) }
      .setEnableBooleanSupplier { goLiveButton.isEnabled && !goLiveButton.isSelected && stageView!!.supportsStreaming() }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
      .build()
    val detachAction = DefaultContextMenuItem.Builder(DETACH_LIVE).setContainerComponent(containerComponent)
      .setActionRunnable { goLiveButton.doClick(0) }
      .setEnableBooleanSupplier { goLiveButton.isEnabled && goLiveButton.isSelected && stageView!!.supportsStreaming() }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)).build()
    goLiveButton.toolTipText = detachAction.defaultToolTipText
    goLiveButton.addActionListener {
      val currentStageTimeline = stageView!!.stage.timeline
      // b/221920489 Hot key may trigger this action from another stage without the streaming timeline
      if (currentStageTimeline is StreamingTimeline) {
        currentStageTimeline.toggleStreaming()
        studioProfilers.ideServices.featureTracker.trackToggleStreaming()
      }
    }
    goLiveButton.addChangeListener {
      val isSelected = goLiveButton.isSelected
      goLiveButton.icon = if (isSelected) StudioIcons.Profiler.Toolbar.PAUSE_LIVE else StudioIcons.Profiler.Toolbar.GOTO_LIVE
      goLiveButton.toolTipText = if (isSelected) detachAction.defaultToolTipText else attachAction.defaultToolTipText
    }
    studioProfilers.timeline.addDependency(this).onChange(StreamingTimeline.Aspect.STREAMING) { updateStreaming() }
    goLiveToolbar.add(goLiveButton)
    timelineNavigationToolbar.add(goLiveToolbar)

    ProfilerContextMenu.createIfAbsent(stageComponent).add(attachAction,
                                                           detachAction,
                                                           ContextMenuItem.SEPARATOR,
                                                           zoomInAction,
                                                           zoomOutAction)

    studioProfilers.sessionsManager.addDependency(this).onChange(SessionAspect.SELECTED_SESSION) { toggleTimelineButtons() }
    toggleTimelineButtons()

    customStageToolbar = JPanel(BorderLayout())
    toolbar.add(customStageToolbar, BorderLayout.CENTER)

    stageComponent.add(toolbar, BorderLayout.NORTH)
    stageComponent.add(stageCenterComponent, BorderLayout.CENTER)

    updateStreaming()
  }

  private fun toggleTimelineButtons() {
    val isAlive = studioProfilers.sessionsManager.isSessionAlive
    if (isAlive) {
      val agentData = studioProfilers.agentData
      val waitForAgent = agentData.status == AgentData.Status.UNSPECIFIED
      if (waitForAgent) {
        // Disable all controls if the agent is still initialization/attaching.
        zoomOutButton.isEnabled = false
        zoomInButton.isEnabled = false
        resetZoomButton.isEnabled = false
        zoomToSelectionButton.isEnabled = false
        goLiveButton.isEnabled = false
        goLiveButton.isSelected = false
      }
      else {
        zoomOutButton.isEnabled = true
        zoomInButton.isEnabled = true
        resetZoomButton.isEnabled = true
        zoomToSelectionButton.isEnabled = zoomToSelectionAction.isEnabled
        goLiveButton.isEnabled = true
        goLiveButton.isSelected = true
      }
    }
    else {
      val isValidSession = Common.Session.getDefaultInstance() != studioProfilers.sessionsManager.selectedSession
      zoomOutButton.isEnabled = isValidSession
      zoomInButton.isEnabled = isValidSession
      resetZoomButton.isEnabled = isValidSession
      zoomToSelectionButton.isEnabled = isValidSession && zoomToSelectionAction.isEnabled
      goLiveButton.isEnabled = false
      goLiveButton.isSelected = false
    }
  }

  private fun updateStreaming() {
    goLiveButton.isSelected = studioProfilers.timeline.isStreaming
  }

  private fun updateStageView(stageViewBuilder: Function<Stage<*>, StageView<*>>, containerComponent: JComponent) {
    val stage = studioProfilers.stage
    if (stageView?.stage === stage) {
      return
    }

    stageView?.stage?.timeline?.selectionRange?.removeDependencies(this)
    stageView = stageViewBuilder.apply(stage)
    stageView!!.stage.timeline.selectionRange.addDependency(this).onChange(Range.Aspect.RANGE) {
      zoomToSelectionButton.isEnabled = zoomToSelectionAction.isEnabled
    }

    SwingUtilities.invokeLater {
      val focussed = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      if (focussed == null || !SwingUtilities.isDescendingFrom(focussed, containerComponent)) {
        containerComponent.requestFocusInWindow()
      }
    }

    stageCenterComponent.removeAll()
    stageCenterComponent.add(stageView!!.component, STAGE_VIEW_CARD)
    stageCenterComponent.add(stageLoadingPanel.component, LOADING_VIEW_CARD)
    stageCenterComponent.revalidate()

    customStageToolbar.removeAll()
    customStageToolbar.add(stageView!!.toolbar, BorderLayout.CENTER)
    customStageToolbar.revalidate()

    toolbar.isVisible = stageView!!.isToolbarVisible
    goLiveToolbar.isVisible = stageView!!.supportsStreaming()

    val topLevel = stageView!!.needsProcessSelection()
    if (!studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
      stageNavigationToolbar.isVisible = !topLevel && stageView!!.supportsStageNavigation()
    }
    timelineNavigationToolbar.isVisible = stage.isInteractingWithTimeline
  }

  private fun toggleStageLayout() {

    // Show the loading screen if StudioProfilers is waiting for a process to profile or if it is waiting for an agent to attach.
    var loading = studioProfilers.autoProfilingEnabled &&
                  studioProfilers.preferredProcessName != null &&
                  !studioProfilers.sessionsManager.isSessionAlive
    val agentData = studioProfilers.agentData
    loading = loading or (agentData.status == AgentData.Status.UNSPECIFIED && studioProfilers.sessionsManager.isSessionAlive)

    // Show the loading screen only if the device is supported.
    loading = loading and (studioProfilers.device != null && studioProfilers.device!!.unsupportedReason.isEmpty())

    if (loading) {
      stageLoadingPanel.startLoading()
      stageCenterCardLayout.show(stageCenterComponent, LOADING_VIEW_CARD)
    }
    else {
      stageLoadingPanel.stopLoading()
      stageCenterCardLayout.show(stageCenterComponent, STAGE_VIEW_CARD)
    }

    toggleTimelineButtons()
  }

  companion object {
    const val ATTACH_LIVE = "Attach to live"
    const val DETACH_LIVE = "Detach live"
    const val ZOOM_IN = "Zoom in"
    const val ZOOM_OUT = "Zoom out"
    private val SHORTCUT_MODIFIER_MASK_NUMBER = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
    private const val LOADING_VIEW_CARD = "LoadingViewCard"
    private const val STAGE_VIEW_CARD = "StageViewCard"
  }
}

