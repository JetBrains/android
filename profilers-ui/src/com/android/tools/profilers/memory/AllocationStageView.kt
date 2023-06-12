package com.android.tools.profilers.memory

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.stdui.CloseButton
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerCombobox
import com.android.tools.profilers.ProfilerComboboxCellRenderer
import com.android.tools.profilers.ProfilerLayout
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

class AllocationStageView(profilersView: StudioProfilersView, stage: AllocationStage)
  : BaseStreamingMemoryProfilerStageView<AllocationStage>(profilersView, stage) {

  private val capturePanel = CapturePanel(profilersView,
                                          stage.captureSelection,
                                          captureElapsedTimeLabel,
                                          stage.rangeSelectionModel.selectionRange,
                                          ideComponents,
                                          stage.timeline,
                                          false)

  @VisibleForTesting
  val timelineComponent = AllocationTimelineComponent(this, buildTimeAxis(profilersView.studioProfilers))

  private val titleLabel = JBLabel().apply { border = JBUI.Borders.empty(0, 5, 0, 0) }

  @VisibleForTesting
  val selectAllButton = CommonButton(StudioIcons.Profiler.Toolbar.SELECT_ENTIRE_RANGE).apply {
    toolTipText = "Track allocations over the entire range"
    addActionListener { stage.selectAll() }
  }

  @VisibleForTesting
  val stopButton = CommonButton(StudioIcons.Profiler.Toolbar.STOP_RECORDING).apply {
    disabledIcon = IconLoader.getDisabledIcon(icon)
    toolTipText = "Stop recording Java / Kotlin allocations"
    addActionListener {
      stage.stopTracking()
      hideLiveButtons()
    }
  }

  @VisibleForTesting
  val forceGcButton = CommonButton(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION).apply {
    disabledIcon = IconLoader.getDisabledIcon(icon)
    toolTipText = "Force the JVM garbage collector to release unused memory"
    addActionListener {
      stage.forceGarbageCollection()
      stage.studioProfilers.ideServices.featureTracker.trackForceGc()
    }
  }

  @VisibleForTesting
  val samplingMenu = AllocationSamplingMenu(stage)

  private val instanceDetailsSplitter = JBSplitter(false).apply {
    border = DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }

  private val instanceDetailsWrapper = JBPanel<Nothing>(BorderLayout()).apply {
    val headingPanel = JBPanel<Nothing>(BorderLayout()).apply {
      border = DEFAULT_HORIZONTAL_BORDERS
      add(titleLabel, BorderLayout.WEST)
      add(CloseButton { stage.captureSelection.selectClassSet(null) }, BorderLayout.EAST)
    }
    add(headingPanel, BorderLayout.NORTH)
    add(instanceDetailsSplitter, BorderLayout.CENTER)
  }

  private val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = capturePanel.component
    secondComponent = instanceDetailsWrapper
  }

  private val trackingPanel = JBSplitter(true).apply {
    firstComponent = timelineComponent
    secondComponent = chartCaptureSplitter
    proportion = .2f
  }
  @VisibleForTesting
  var loadingPanel: LoadingPanel? = null
  private val mainPanelLayout = CardLayout()
  private val mainPanel = JPanel(mainPanelLayout).apply {
    add(trackingPanel, CARD_TRACKING)
  }

  init {
    fun updateInstanceDetailsSplitter() = when (val cs = stage.captureSelection.selectedClassSet) {
      null -> instanceDetailsWrapper.isVisible = false
      else -> {
        titleLabel.text = "Instance List - ${cs.name}"
        instanceDetailsWrapper.isVisible = true
      }
    }

    fun updateLabel() {
      val elapsedUs = stage.minTrackingTimeUs.toLong() - TimeUnit.NANOSECONDS.toMicros(stage.studioProfilers.session.startTimestamp)
      captureElapsedTimeLabel.text = "Recorded Java / Kotlin Allocations: ${TimeFormatter.getSimplifiedClockString(elapsedUs)}"
    }

    stage.captureSelection.aspect.addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_CLASS, ::updateInstanceDetailsSplitter)
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
    }
    else {
      stage.aspect.addDependency(this).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_STATUS) { showTrackingeUi() }
      showLoadingPanel()
    }
    component.add(mainPanel, BorderLayout.CENTER)

    mainPanel.addHierarchyListener {
      if (!mainPanel.isDisplayable || !mainPanel.isShowing) {
        hideLoadingPanel()
      }
    }
  }

  override fun getToolbar() = JBPanel<Nothing>(BorderLayout()).apply {
    val toolbar = JBPanel<Nothing>(createToolbarLayout()).apply {
      add(captureElapsedTimeLabel)
      add(FlatSeparator())
      add(selectAllButton)
      add(stopButton)
      add(forceGcButton)
      add(FlatSeparator())
      add(samplingMenu)
    }
    add(toolbar, BorderLayout.WEST)
    hideLiveButtons()
  }

  private fun hideLiveButtons() {
    if (stage.hasEndedTracking) {
      forceGcButton.isVisible = false
      samplingMenu.isVisible = false
      stopButton.isVisible = false
    }
  }

  private fun adjustSelectAllButton() {
    selectAllButton.isEnabled = !stage.isAlmostAllSelected()
  }

  private fun showTrackingeUi() {
    hideLoadingPanel()
    mainPanelLayout.show(mainPanel, CARD_TRACKING)
  }

  private fun showLoadingPanel() {
    if (loadingPanel == null)
      profilersView.ideProfilerComponents.createLoadingPanel(-1).apply {
        setLoadingText("Setting up allocation tracking")
      }.let {
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
    fun rebase(r: Range) = Range(0.0, r.max - r.min).apply {
      r.addDependency(this).onChange(Range.Aspect.RANGE) { max = r.max - r.min }
    }
    val model = ResizingAxisComponentModel.Builder(rebase(profilers.timeline.viewRange), TimeAxisFormatter.DEFAULT)
      .setGlobalRange(rebase(profilers.timeline.dataRange))
      .build()
    val timeAxis = AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM, true).apply {
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
  }
}

class AllocationTimelineComponent(stageView: AllocationStageView, timeAxis: JComponent)
  : BaseMemoryTimelineComponent<AllocationStage>(stageView, timeAxis) {

  val gcDurationDataRenderer = makeGcDurationDataRenderer().also(::registerRenderer)
  val allocationSamplingRateRenderer = makeAllocationSamplingRateRenderer().also(::registerRenderer)

  override fun makeScrollbar() = null // the timeline always contains the allocation range, so no need for scrollbar

  override fun makeLineChart() = super.makeLineChart().apply {
    // Dynamically fill up to the latest point in data-range (instead of all the way to the right by default)
    setFillEndSupplier { (stage.timeline.dataRange.max - stage.minTrackingTimeUs) / (stage.timeline.viewRange.max - stage.minTrackingTimeUs) }
  }
}

class AllocationSamplingMenu(private val stage: AllocationStage): JBPanel<AllocationSamplingMenu>(BorderLayout()) {
  private val label = JBLabel("Allocation Tracking")
  val combobox = ProfilerCombobox<LiveAllocationSamplingMode>()
  private val observer = AspectObserver()
  private val logger get() = Logger.getInstance(AllocationStageView::class.java)

  init {
    combobox.apply {
      model = DefaultComboBoxModel(arrayOf(FULL, SAMPLED))
      renderer = object : ProfilerComboboxCellRenderer<LiveAllocationSamplingMode>() {
        override fun customizeCellRenderer(list: JList<out LiveAllocationSamplingMode>,
                                           value: LiveAllocationSamplingMode?,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          append(value?.displayName ?: "-----")
        }
      }
      addActionListener {
        stage.requestLiveAllocationSamplingModeUpdate(model.selectedItem as LiveAllocationSamplingMode)
      }
    }
    stage.aspect.addDependency(observer).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE, ::onSamplingModeChanged)
    onSamplingModeChanged()

    border = JBUI.Borders.empty(0, 5)
    add(label, BorderLayout.LINE_START)
    add(combobox, BorderLayout.CENTER)
  }

  private fun onSamplingModeChanged() = when (val mode = stage.liveAllocationSamplingMode) {
    FULL, SAMPLED -> { combobox.model.selectedItem = mode }
    NONE -> {}
  }
}