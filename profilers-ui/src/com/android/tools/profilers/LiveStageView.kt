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
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

class LiveStageView(profilersView: StudioProfilersView, liveStage: LiveStage) :
  StageView<LiveStage>(profilersView, liveStage) {

  val binder: ViewBinder<StudioProfilersView, LiveDataModel, LiveDataView<out LiveDataModel>> = ViewBinder()
  private val stopRecordingButton: CommonButton
  private val liveStageToolbarItems: MutableList<JComponent>

  init {
    binder.bind(
      LiveMemoryFootprintModel::class.java) { view: StudioProfilersView, model
      -> LiveMemoryFootprintView(view, model)
    }
    binder.bind(LiveCpuUsageModel::class.java) { view: StudioProfilersView, model
      -> LiveCpuUsageView(view, model)
    }

    val liveViewLayout = TabularLayout("*")
    val liveViews = JPanel(liveViewLayout)
    liveViews.background = ProfilerColors.DEFAULT_BACKGROUND

    tooltipBinder.bind(LifecycleTooltip::class.java) { stageView: LiveStageView, tooltip
      -> LifecycleTooltipView(stageView.component, tooltip)
    }
    tooltipBinder.bind(UserEventTooltip::class.java) { stageView: LiveStageView, tooltip
      -> UserEventTooltipView(stageView.component, tooltip)
    }

    stopRecordingButton = createStopRecordingButton()
    liveStageToolbarItems =  createLiveStageToolbarItems()

    tooltipPanel.layout = FlowLayout(FlowLayout.LEFT, 0, 0)
    val myTooltipComponent = RangeTooltipComponent(
      stage.timeline,
      tooltipPanel,
      profilersView.component,
      this::shouldShowTooltipSeekComponent
    )

    val topPanelLayout = TabularLayout("*", "*,Fit-")
    val topPanel = JPanel(topPanelLayout)
    topPanel.background = ProfilerColors.DEFAULT_STAGE_BACKGROUND

    topPanelLayout.setRowSizing(0, "Fit-")

    if (liveStage.eventMonitor.isPresent) {
      liveStage.eventMonitor.let { eventMonitor ->
        val eventsView = EventMonitorView(profilersView, eventMonitor.get())
        val eventComponent = eventsView.component
        eventsView.registerTooltip(myTooltipComponent, stage)
        topPanelLayout.setRowSizing(1, "Fit-")
        topPanel.add(eventComponent, TabularLayout.Constraint(1, 0))
      }
    }

    for ((rowIndex, liveDataModel) in liveStage.liveModels.withIndex()) {
      liveDataModel.enter()
      val view = binder.build(profilersView, liveDataModel) as LiveDataView<LiveDataModel>
      val viewComponent = view.component
      view.populateUi(myTooltipComponent)
      view.registerTooltip(tooltipBinder, myTooltipComponent, liveStage)
      liveViews.add(viewComponent, TabularLayout.Constraint(rowIndex, 0))
      liveViewLayout.setRowSizing(rowIndex, rowSizeString(view))
    }

    topPanelLayout.setRowSizing(2, "*")
    topPanel.add(liveViews, TabularLayout.Constraint(2, 0))

    val profilers = liveStage.studioProfilers
    val timeAxis = buildTimeAxis(profilers)
    topPanel.add(timeAxis, TabularLayout.Constraint(3, 0))
    topPanel.add(TimelineScrollbar(liveStage.timeline, topPanel), TabularLayout.Constraint(4, 0))

    component.add(topPanel, BorderLayout.CENTER)
  }

  private val isLiveRecordingOngoing get() = stage.studioProfilers.sessionsManager.isSessionAlive

  private fun onStopRecordingClick() {
    stage.stopTask.invoke()
    stopRecordingButton.isVisible = false

    // Live model icons in toolbar is set to invisible on stop recording button click
    liveStageToolbarItems.forEach{ it.isVisible = false }
  }

  private fun createStopRecordingButton(): CommonButton {
    val button = CommonButton(
      if (profilersView.studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
        StudioIcons.Profiler.Toolbar.STOP_SESSION
      }
      else {
        StudioIcons.Profiler.Toolbar.STOP_RECORDING
      }
    ).apply {
      toolTipText = Companion.stopRecordingTooltip
      addActionListener {
        onStopRecordingClick()
      }
      putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
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
    return if (weight > 0) "$weight*" else "Fit-"
  }

  override fun getToolbar(): JComponent {
    val panel = JPanel(BorderLayout())
    val toolbar = JPanel(ProfilerLayout.createToolbarLayout())
    toolbar.removeAll()
    liveStageToolbarItems.forEach{toolbar.add(it)}
    toolbar.add(stopRecordingButton)
    panel.add(toolbar, BorderLayout.WEST)
    return panel
  }

  companion object {
    private const val showDebuggableMessage = "debuggable.monitor.message"
    private const val showProfileableMessage = "profileable.monitor.message"
    private const val stopRecordingTooltip = "Stop Recording"
    fun getMessage(studioProfiler: StudioProfilers): JComponent {
      return when (studioProfiler.selectedSessionSupportLevel) {
        SupportLevel.DEBUGGABLE -> DismissibleMessage.of(studioProfiler,
                                                         showDebuggableMessage,
                                                         "Profiling as debuggable. This does not represent app performance in production." +
                                                         " Consider profiling as profileable.",
                                                         SupportLevel.DOC_LINK)

        SupportLevel.PROFILEABLE -> DismissibleMessage.of(studioProfiler,
                                                          showProfileableMessage,
                                                          "Profiling as profileable. Certain profiler features will be unavailable in this mode.",
                                                          SupportLevel.DOC_LINK)

        else -> {
          JPanel()
        }
      }
    }
  }

  private fun shouldShowTooltipSeekComponent() = true
}