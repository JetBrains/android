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

import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.TimelineScrollbar
import com.android.tools.profilers.cpu.LiveCpuUsageModel
import com.android.tools.profilers.cpu.LiveCpuUsageView
import com.android.tools.profilers.event.EventMonitorView
import com.android.tools.profilers.event.LifecycleTooltip
import com.android.tools.profilers.event.LifecycleTooltipView
import com.android.tools.profilers.event.UserEventTooltip
import com.android.tools.profilers.event.UserEventTooltipView
import com.android.tools.profilers.memory.LiveMemoryFootprintModel
import com.android.tools.profilers.memory.LiveMemoryFootprintView
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class LiveStageView(profilersView: StudioProfilersView, liveStage: LiveStage) : StageView<LiveStage>(profilersView, liveStage) {

  val binder: ViewBinder<StudioProfilersView, LiveDataModel, LiveDataView<out LiveDataModel>> = ViewBinder()
  private val stopRecordingButton: JButton
  private val liveStageToolbarItems: MutableList<JComponent>

  private val tooltipPanels = mutableListOf<JPanel>()
  private val activeTooltipViews = mutableListOf<TooltipView>()
  val recordingTimerLabel = JBLabel("").apply { foreground = ProfilerColors.CPU_CAPTURE_STATUS }
  private val recordingSeparator = FlatSeparator().apply { isVisible = false }
  val leftToolbar =
    JPanel(ProfilerLayout.createToolbarLayout()).apply {
      border = JBUI.Borders.emptyLeft(5)
      add(recordingTimerLabel)
    }

  init {
    binder.bind(LiveMemoryFootprintModel::class.java) { view: StudioProfilersView, model -> LiveMemoryFootprintView(view, model) }
    binder.bind(LiveCpuUsageModel::class.java) { view: StudioProfilersView, model -> LiveCpuUsageView(view, model) }

    val liveViewLayout = TabularLayout("*")
    val liveViews = JPanel(liveViewLayout)
    liveViews.background = ProfilerColors.DEFAULT_BACKGROUND

    tooltipBinder.bind(LifecycleTooltip::class.java) { stageView: LiveStageView, tooltip ->
      LifecycleTooltipView(stageView.component, tooltip)
    }
    tooltipBinder.bind(UserEventTooltip::class.java) { stageView: LiveStageView, tooltip ->
      UserEventTooltipView(stageView.component, tooltip)
    }

    stopRecordingButton = createStopRecordingButton()
    liveStageToolbarItems = createLiveStageToolbarItems()

    tooltipPanel.layout = FlowLayout(FlowLayout.LEFT, 0, 0)

    val topPanelLayout = TabularLayout("*", "*,Fit-")
    val topPanel = JPanel(topPanelLayout)
    topPanel.background = ProfilerColors.DEFAULT_STAGE_BACKGROUND

    topPanelLayout.setRowSizing(0, "Fit-")

    if (liveStage.eventMonitor.isPresent) {
      liveStage.eventMonitor.let { eventMonitor ->
        val eventsView = EventMonitorView(profilersView, eventMonitor.get())
        val eventComponent = eventsView.component
        val eventTooltipPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { background = ProfilerColors.TOOLTIP_BACKGROUND }
        tooltipPanels.add(eventTooltipPanel)
        val eventTooltipComponent =
          RangeTooltipComponent(stage.timeline, eventTooltipPanel, profilersView.component, this::shouldShowTooltipSeekComponent)
        eventsView.registerTooltip(eventTooltipComponent, stage)
        topPanelLayout.setRowSizing(1, "Fit-")
        topPanel.add(eventComponent, TabularLayout.Constraint(1, 0))
      }
    }

    for ((rowIndex, liveDataModel) in liveStage.liveModels.withIndex()) {
      liveDataModel.enter()
      val view = binder.build(profilersView, liveDataModel) as LiveDataView<LiveDataModel>
      val viewComponent = view.component
      val liveTooltipPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { background = ProfilerColors.TOOLTIP_BACKGROUND }
      tooltipPanels.add(liveTooltipPanel)
      val tooltipComponent =
        RangeTooltipComponent(stage.timeline, liveTooltipPanel, profilersView.component, this::shouldShowTooltipSeekComponent)
      view.populateUi(tooltipComponent)
      view.registerTooltip(tooltipBinder, tooltipComponent, liveStage)
      liveViews.add(viewComponent, TabularLayout.Constraint(rowIndex, 0))
      liveViewLayout.setRowSizing(rowIndex, rowSizeString(view))
      view.addWeightChangedListener {
        liveViewLayout.setRowSizing(rowIndex, rowSizeString(view))
        liveViews.revalidate()
        liveViews.repaint()
      }
    }

    topPanelLayout.setRowSizing(2, "*")
    topPanel.add(liveViews, TabularLayout.Constraint(2, 0))

    val profilers = liveStage.studioProfilers
    val timeAxis = buildTimeAxis(profilers)
    topPanel.add(timeAxis, TabularLayout.Constraint(3, 0))
    topPanel.add(TimelineScrollbar(liveStage.timeline, topPanel), TabularLayout.Constraint(4, 0))

    component.add(topPanel, BorderLayout.CENTER)

    // For completed sessions, the data models miss the initial "range changed" event from the timeline, so they never load their data.
    // By manually triggering a refresh here in the view's constructor, we can be certain that the view is ready and listening for the
    // resulting "data changed" event, ensuring the data is loaded and displayed correctly.
    //
    // This is only necessary for models whose UI components are event-driven (like the DurationDataRenderer for GC events). Other models
    // (like LiveCpuUsageModel) use a "continuous pull" pattern where the view redraws on every frame, which is not affected by the
    // initial missed event.
    stage.liveModels.filterIsInstance<LiveMemoryFootprintModel>().firstOrNull()?.refreshModels()

    fun updateTimer() {
      if (!isLiveRecordingOngoing || isStopping) {
        recordingTimerLabel.text = ""
        recordingTimerLabel.isVisible = false
        recordingTimerLabel.icon = null
        updateSeparatorVisibility()
        return
      }
      val elapsedUs = (stage.timeline.dataRange.max - stage.timeline.dataRange.min).coerceAtLeast(0.0).toLong()
      val elapsedNs = TimeUnit.MICROSECONDS.toNanos(elapsedUs)
      val formattedTime = RecordingScreenModel.formatElapsedTime(elapsedNs)
      recordingTimerLabel.icon = StudioIcons.Profiler.Toolbar.STOP_RECORDING
      recordingTimerLabel.text = "<html><b>Recording:</b> $formattedTime</html>"
      recordingTimerLabel.isVisible = true
      updateSeparatorVisibility()
    }

    stage.timeline.dataRange.addDependency(this).onChange(Range.Aspect.RANGE, ::updateTimer)
    updateTimer()
  }

  private fun updateSeparatorVisibility() {
    recordingSeparator.isVisible = isLiveRecordingOngoing && !isStopping && recordingSeparator.parent == leftToolbar
  }

  private val isLiveRecordingOngoing
    get() = stage.studioProfilers.sessionsManager.isSessionAlive

  private var isStopping = false

  private fun onStopRecordingClick() {
    isStopping = true
    stage.stopTask.invoke()
    stopRecordingButton.isVisible = false
    recordingTimerLabel.isVisible = false
    recordingTimerLabel.text = ""
    recordingTimerLabel.icon = null
    updateSeparatorVisibility()

    // Live model icons in toolbar is set to invisible on stop recording button click
    liveStageToolbarItems.forEach { it.isVisible = false }
  }

  private fun createStopRecordingButton(): JButton {
    val button =
      if (profilersView.studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
          JButton(TaskBasedUxStrings.ACTION_BAR_STOP_RECORDING).apply {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            font = font.deriveFont(font.style)
          }
        } else {
          CommonButton(StudioIcons.Profiler.Toolbar.STOP_RECORDING).apply { disabledIcon = IconLoader.getDisabledIcon(icon) }
        }
        .apply {
          toolTipText = Companion.stopRecordingTooltip
          addActionListener { onStopRecordingClick() }
        }
    button.isVisible = isLiveRecordingOngoing
    return button
  }

  private fun createLiveStageToolbarItems(): ArrayList<JComponent> {
    val liveModelToolbarItems: ArrayList<JComponent> = ArrayList()
    if (!isLiveRecordingOngoing) {
      return liveModelToolbarItems
    }
    for (liveDataModel in stage.liveModels) {
      val liveDataView: LiveDataView<out LiveDataModel> = binder.build(profilersView, liveDataModel)
      liveDataView.toolbar?.let { liveModelToolbarItems.add(it) }
    }
    return liveModelToolbarItems
  }

  private fun rowSizeString(view: LiveDataView<LiveDataModel>): String {
    val weight = (view.verticalWeight * 100f).toInt()
    return if (weight > 0) "$weight*" else "Fit"
  }

  override fun tooltipChanged() {
    activeTooltipViews.forEach { it.dispose() }
    activeTooltipViews.clear()
    for (panel in tooltipPanels) {
      panel.removeAll()
      panel.isVisible = false
    }

    val tooltip = stage.tooltip
    if (tooltip != null) {
      for (panel in tooltipPanels) {
        val view = tooltipBinder.build(this, tooltip)
        activeTooltipViews.add(view)
        panel.add(view.createComponent())
        panel.isVisible = true
      }
    }
    for (panel in tooltipPanels) {
      panel.invalidate()
      panel.repaint()
    }
  }

  fun addLeftToolbarComponent(component: JComponent) {
    if (component.parent != leftToolbar) {
      if (recordingSeparator.parent != leftToolbar) {
        leftToolbar.add(recordingSeparator)
      }
      leftToolbar.add(component)
      updateSeparatorVisibility()
    }
  }

  override fun getToolbar(): JComponent {
    val panel = JPanel(BorderLayout())
    val rightToolbar =
      JPanel(ProfilerLayout.createToolbarLayout()).apply {
        border = JBUI.Borders.emptyRight(5)
        liveStageToolbarItems.forEach { add(it, GridBagConstraints().apply { insets = JBUI.insets(0, 0, 0, 0) }) }
        add(stopRecordingButton, GridBagConstraints().apply { insets = JBUI.insets(0, 2, 0, 0) })
      }
    panel.add(leftToolbar, BorderLayout.WEST)
    panel.add(rightToolbar, BorderLayout.EAST)
    return panel
  }

  companion object {
    private const val showDebuggableMessage = "debuggable.monitor.message"
    private const val showProfileableMessage = "profileable.monitor.message"
    private const val stopRecordingTooltip = "Stop Recording"

    fun getMessage(studioProfiler: StudioProfilers): JComponent {
      return when (studioProfiler.selectedSessionSupportLevel) {
        SupportLevel.DEBUGGABLE ->
          DismissibleMessage.of(
            studioProfiler,
            showDebuggableMessage,
            "Profiling as debuggable. This does not represent app performance in production." + " Consider profiling as profileable.",
            SupportLevel.DOC_LINK,
          )

        SupportLevel.PROFILEABLE ->
          DismissibleMessage.of(
            studioProfiler,
            showProfileableMessage,
            "Profiling as profileable. Certain profiler features will be unavailable in this mode.",
            SupportLevel.DOC_LINK,
          )

        else -> {
          JPanel()
        }
      }
    }
  }

  private fun shouldShowTooltipSeekComponent() = true
}
