package com.android.tools.profilers.memory

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.stdui.CloseButton
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerDropDownComponent
import com.android.tools.profilers.ProfilerFlows
import com.android.tools.profilers.ProfilerLayout
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.Selection
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.util.concurrent.TimeUnit
import java.util.function.DoubleSupplier
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class AllocationStageView(profilersView: StudioProfilersView, stage: AllocationStage) :
  BaseStreamingMemoryProfilerStageView<AllocationStage>(profilersView, stage) {

  private val capturePanel =
    CapturePanel(
      profilersView,
      stage.captureSelection,
      captureElapsedTimeLabel,
      stage.rangeSelectionModel.selectionRange,
      ideComponents,
      stage.timeline,
      false,
    )

  private val garbageCollectionComponent = GarbageCollectionComponent()

  @VisibleForTesting val timelineComponent = AllocationTimelineComponent(this, buildTimeAxis(profilersView.studioProfilers))

  private val titleLabel = JBLabel().apply { border = JBUI.Borders.empty(0, 5, 0, 0) }

  @VisibleForTesting
  val selectAllButton =
    CommonButton(StudioIcons.Profiler.Toolbar.SELECT_ENTIRE_RANGE).apply {
      toolTipText = "Track allocations over the entire range"
      addActionListener { stage.selectAll() }
    }

  @VisibleForTesting
  val stopButton =
    if (profilersView.studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
        JButton(TaskBasedUxStrings.ACTION_BAR_STOP_RECORDING).apply {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          font = font.deriveFont(font.style)
        }
      } else {
        CommonButton(StudioIcons.Profiler.Toolbar.STOP_RECORDING).apply { disabledIcon = IconLoader.getDisabledIcon(icon) }
      }
      .apply {
        toolTipText = "Stop recording Java / Kotlin allocations"
        addActionListener {
          if (getStage().studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
            stage.stopTask.invoke()
          } else {
            stage.stopTracking()
          }
          hideLiveButtons()
        }
      }

  @VisibleForTesting
  val forceGcButton = garbageCollectionComponent.makeGarbageCollectionButton(stage.memoryDataProvider, profilersView.studioProfilers)

  @VisibleForTesting val samplingMenu = AllocationSamplingMenu(stage)

  private val recordingSeparator = FlatSeparator()

  private val instanceDetailsSplitter =
    JBSplitter(false).apply {
      border = DEFAULT_VERTICAL_BORDERS
      isOpaque = true
      firstComponent = capturePanel.classSetView.component
      secondComponent = capturePanel.instanceDetailsView.component
    }

  private val instanceDetailsWrapper =
    JBPanel<Nothing>(BorderLayout()).apply {
      val headingPanel =
        JBPanel<Nothing>(BorderLayout()).apply {
          border = DEFAULT_HORIZONTAL_BORDERS
          add(titleLabel, BorderLayout.WEST)
          add(CloseButton { stage.captureSelection.selectClassSet(null) }, BorderLayout.EAST)
        }
      add(headingPanel, BorderLayout.NORTH)
      add(instanceDetailsSplitter, BorderLayout.CENTER)
    }

  private val chartCaptureSplitter =
    JBSplitter(true).apply {
      border = DEFAULT_VERTICAL_BORDERS
      firstComponent = capturePanel.component
      secondComponent = instanceDetailsWrapper
    }

  private val trackingPanel =
    JBSplitter(true).apply {
      firstComponent = timelineComponent
      secondComponent = chartCaptureSplitter
      proportion = .3f
    }
  @VisibleForTesting var loadingPanel: LoadingPanel? = null
  private val mainPanelLayout = CardLayout()
  private val mainPanel = JPanel(mainPanelLayout).apply { add(trackingPanel, CARD_TRACKING) }

  init {
    fun updateInstanceDetailsSplitter() =
      when (val cs = stage.captureSelection.selectedClassSet) {
        null -> instanceDetailsWrapper.isVisible = false
        else -> {
          titleLabel.text = "Instance List - ${cs.name}"
          instanceDetailsWrapper.isVisible = true
        }
      }

    fun updateLabel() {
      if (stage.hasEndedTracking || stage.isStatic) {
        captureElapsedTimeLabel.text = ""
        captureElapsedTimeLabel.isVisible = false
        captureElapsedTimeLabel.icon = null
        recordingSeparator.isVisible = false
        return
      }
      val elapsedUs =
        if (stage.hasStartedTracking) {
          (stage.timeline.dataRange.max - stage.minTrackingTimeUs).coerceAtLeast(0.0).toLong()
        } else 0L
      val elapsedNs = TimeUnit.MICROSECONDS.toNanos(elapsedUs)
      val formattedTime = RecordingScreenModel.formatElapsedTime(elapsedNs)
      captureElapsedTimeLabel.icon = StudioIcons.Profiler.Toolbar.STOP_RECORDING
      captureElapsedTimeLabel.text = "<html><b>Recording:</b> $formattedTime</html>"
      captureElapsedTimeLabel.isVisible = true
      recordingSeparator.isVisible = true
    }

    stage.captureSelection.aspect.addDependency(this).onChange(CaptureSelectionAspect.CURRENT_CLASS, ::updateInstanceDetailsSplitter)
    stage.timeline.selectionRange.addDependency(this).onChange(Range.Aspect.RANGE, ::adjustSelectAllButton)
    stage.timeline.dataRange.addDependency(this).onChange(Range.Aspect.RANGE) {
      adjustSelectAllButton()
      updateLabel()
    }
    stage.studioProfilers.sessionsManager.addDependency(this).onChange(SessionAspect.SESSIONS) {
      if (!stage.studioProfilers.sessionsManager.isSessionAlive) {
        stopButton.doClick()
        // Also stop loading panel if the session is terminated before successful loading
        loadingPanel?.stopLoading()
      }
    }
    updateLabel()
    updateInstanceDetailsSplitter()
    if (stage.isStatic) {
      showTrackingeUi()
    } else {
      stage.aspect.addDependency(this).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_STATUS) { showTrackingeUi() }
      if (stage.hasAgentError) showErrorPanel() else showLoadingPanel()
    }
    component.add(mainPanel, BorderLayout.CENTER)

    mainPanel.addHierarchyListener {
      if (!mainPanel.isDisplayable || !mainPanel.isShowing) {
        hideLoadingPanel()
      }
    }
  }

  val leftToolbar =
    JBPanel<Nothing>(createToolbarLayout()).apply {
      border = JBUI.Borders.emptyLeft(5)
      add(captureElapsedTimeLabel)
      add(recordingSeparator)
      add(selectAllButton)
    }

  override fun getToolbar() =
    JBPanel<Nothing>(BorderLayout()).apply {
      val rightToolbar =
        JBPanel<Nothing>(createToolbarLayout()).apply {
          border = JBUI.Borders.emptyRight(5)
          add(samplingMenu.component, GridBagConstraints().apply { insets = JBUI.insets(0, 0, 0, 0) })
          add(forceGcButton, GridBagConstraints().apply { insets = JBUI.insets(0, 2, 0, 0) })
          add(stopButton, GridBagConstraints().apply { insets = JBUI.insets(0, 2, 0, 0) })
        }
      add(leftToolbar, BorderLayout.WEST)
      add(rightToolbar, BorderLayout.EAST)
      hideLiveButtons()
    }

  private fun hideLiveButtons() {
    if (stage.hasEndedTracking || stage.isStatic) {
      forceGcButton.isVisible = false
      samplingMenu.component.isVisible = false
      stopButton.isVisible = false
      captureElapsedTimeLabel.isVisible = false
      captureElapsedTimeLabel.text = ""
      captureElapsedTimeLabel.icon = null
      recordingSeparator.isVisible = false
    }
  }

  private fun adjustSelectAllButton() {
    selectAllButton.isEnabled = !stage.isAlmostAllSelected()
  }

  private fun showTrackingeUi() {
    hideLoadingPanel()
    if (stage.hasAgentError) showErrorPanel() else mainPanelLayout.show(mainPanel, CARD_TRACKING)
  }

  private fun getLoadingFailureErrorMessage(): String {
    return if (profilersView.studioProfilers.device?.isEmulator == true)
      "There was an error loading this feature. Try cold booting the virtual device."
    else "There was an error loading this feature. Try restarting the device."
  }

  private fun showErrorPanel() {
    hideLiveButtons()
    val errorMessagePanel = JPanel(BorderLayout())
    val message = getLoadingFailureErrorMessage()
    val htmlText = "<html><div style='text-align: center;'> ${StringUtil.escapeXmlEntities(message)} </div></html>"
    val errorText = JBLabel(htmlText)
    errorText.horizontalAlignment = JBLabel.CENTER
    errorText.fontColor = UIUtil.FontColor.BRIGHTER
    errorMessagePanel.add(errorText, BorderLayout.CENTER)
    errorMessagePanel.isVisible = true
    mainPanel.add(errorMessagePanel, CARD_ERROR)
    mainPanelLayout.show(mainPanel, CARD_ERROR)
  }

  private fun showLoadingPanel() {
    if (loadingPanel == null)
      profilersView.ideProfilerComponents
        .createLoadingPanel(-1)
        .apply { setLoadingText("Setting up allocation tracking") }
        .let {
          loadingPanel = it
          it.startLoading()
          mainPanel.add(it.component, CARD_LOADING)
          mainPanelLayout.show(mainPanel, CARD_LOADING)
        }
  }

  private fun hideLoadingPanel() {
    loadingPanel?.let {
      it.stopLoading()
      mainPanel.remove(it.component)
      loadingPanel = null
    }
  }

  // Customize the time axis to start from 0
  override fun buildTimeAxis(profilers: StudioProfilers): JComponent {
    fun rebase(r: Range) = Range(0.0, r.max - r.min).apply { r.addDependency(this).onChange(Range.Aspect.RANGE) { max = r.max - r.min } }
    val model =
      ResizingAxisComponentModel.Builder(rebase(profilers.timeline.viewRange), TimeAxisFormatter.DEFAULT)
        .setGlobalRange(rebase(profilers.timeline.dataRange))
        .build()
    val timeAxis =
      AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM, true).apply {
        setShowAxisLine(false)
        minimumSize = Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT)
        preferredSize = Dimension(Int.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT)
      }
    return JBPanel<Nothing>(BorderLayout()).apply {
      background = ProfilerColors.DEFAULT_BACKGROUND
      add(timeAxis, BorderLayout.CENTER)
    }
  }

  private companion object {
    const val CARD_TRACKING = "tracking"
    const val CARD_LOADING = "loading"
    const val CARD_ERROR = "error"
  }
}

class AllocationTimelineComponent(stageView: AllocationStageView, timeAxis: JComponent) :
  BaseMemoryTimelineComponent<AllocationStage>(stageView, timeAxis) {

  val gcDurationDataRenderer = makeGcDurationDataRenderer().also(::registerRenderer)
  val allocationSamplingRateRenderer = makeAllocationSamplingRateRenderer().also(::registerRenderer)

  override fun makeScrollbar() = null // the timeline always contains the allocation range, so no need for scrollbar

  override fun fillEndSupplier() = DoubleSupplier {
    // Dynamically fill up to the latest point in data-range (instead of all the way to the right by default)
    (stage.timeline.dataRange.max - stage.minTrackingTimeUs) / (stage.timeline.viewRange.max - stage.minTrackingTimeUs)
  }
}

class AllocationSamplingMenu(private val stage: AllocationStage) {
  @get:VisibleForTesting
  val samplingModeFlow =
    ProfilerFlows.createMutableStateFlow(
      Selection(if (stage.liveAllocationSamplingMode == NONE) FULL else stage.liveAllocationSamplingMode, listOf(FULL, SAMPLED))
    )
  private val observer = AspectObserver()

  val dropDown =
    ProfilerDropDownComponent(
      FULL.displayName,
      "Select allocation tracking mode",
      null,
      samplingModeFlow,
      null,
      { mode ->
        samplingModeFlow.value = Selection(mode, listOf(FULL, SAMPLED))
        stage.requestLiveAllocationSamplingModeUpdate(mode)
      },
      { mode -> mode?.displayName ?: FULL.displayName },
    )

  val component: JPanel =
    JPanel(createToolbarLayout()).apply {
      add(
        JLabel("Allocation Tracking:").apply {
          foreground = UIUtil.getLabelDisabledForeground()
          border = JBUI.Borders.empty(4, 12, 0, 2)
        }
      )
      add(dropDown)
    }

  init {
    stage.aspect.addDependency(observer).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE, ::onSamplingModeChanged)
    onSamplingModeChanged()
  }

  private fun onSamplingModeChanged() {
    val mode = if (stage.liveAllocationSamplingMode == NONE) FULL else stage.liveAllocationSamplingMode
    samplingModeFlow.value = Selection(mode, listOf(FULL, SAMPLED))
  }
}
