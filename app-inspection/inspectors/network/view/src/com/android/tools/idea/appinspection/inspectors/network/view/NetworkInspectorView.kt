/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.LegendConfig
import com.android.tools.adtui.RangeSelectionComponent
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.chart.linechart.LineConfig
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangeSelectionListener
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.stdui.StreamingScrollbar
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorAspect
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkTrafficTooltipModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.SelectionRangeDataListener
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_STAGE_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.H3_FONT
import com.android.tools.idea.appinspection.inspectors.network.view.constants.LEGEND_RIGHT_PADDING
import com.android.tools.idea.appinspection.inspectors.network.view.constants.MARKER_LENGTH
import com.android.tools.idea.appinspection.inspectors.network.view.constants.MONITOR_BORDER
import com.android.tools.idea.appinspection.inspectors.network.view.constants.MONITOR_LABEL_PADDING
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_RECEIVING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_SENDING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.STANDARD_FONT
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TIME_AXIS_HEIGHT
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLTIP_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.Y_AXIS_TOP_MARGIN
import com.android.tools.idea.appinspection.inspectors.network.view.details.NetworkInspectorDetailsPanel
import com.android.tools.idea.appinspection.inspectors.network.view.rules.RulesTableView
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtilities
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants


private const val CARD_CONNECTIONS = "Connections"
private const val CARD_INFO = "Info"

/**
 * The main view of network inspector.
 */
class NetworkInspectorView(
  project: Project,
  val model: NetworkInspectorModel,
  val componentsProvider: UiComponentsProvider,
  private val parentPane: TooltipLayeredPane,
  private val inspectorServices: NetworkInspectorServices,
  scope: CoroutineScope
) : AspectObserver() {

  val component = JPanel(BorderLayout())

  /**
   * Container for the tooltip.
   */
  private val tooltipPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

  /**
   * View of the active tooltip for stages that contain more than one tooltips.
   */
  var activeTooltipView: TooltipView? = null

  /**
   * A common component for showing the current selection range.
   */
  private val selectionTimeLabel = createSelectionTimeLabel()

  @VisibleForTesting
  val connectionsView = ConnectionsView(model, parentPane)

  val rulesView = RulesTableView(project, inspectorServices.client, scope, model, inspectorServices.usageTracker)

  @VisibleForTesting
  val detailsPanel = NetworkInspectorDetailsPanel(this, inspectorServices.usageTracker).apply { isVisible = false }
  private val mainPanel = JPanel(TabularLayout("*,Fit-", "Fit-,*"))
  private val tooltipBinder = ViewBinder<NetworkInspectorView, TooltipModel, TooltipView>()

  init {
    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    tooltipPanel.background = TOOLTIP_BACKGROUND
    model.addDependency(this).onChange(NetworkInspectorAspect.TOOLTIP) { tooltipChanged() }
    model.timeline.selectionRange.addDependency(this).onChange(Range.Aspect.RANGE) { selectionChanged() }
    selectionChanged()
    tooltipBinder.bind(NetworkTrafficTooltipModel::class.java) { view: NetworkInspectorView, tooltip ->
      NetworkTrafficTooltipView(view, tooltip)
    }
    detailsPanel.minimumSize = Dimension(JBUI.scale(550), detailsPanel.minimumSize.getHeight().toInt())
    val threadsView = ThreadsView(model, parentPane)
    val leftSplitter = JBSplitter(true, 0.25f)
    leftSplitter.divider.border = DEFAULT_HORIZONTAL_BORDERS
    leftSplitter.firstComponent = buildMonitorUi()
    val connectionsPanel = JPanel(CardLayout())
    val connectionsTab = CommonTabbedPane()
    val connectionScrollPane = JBScrollPane(connectionsView.component)
    connectionScrollPane.border = JBUI.Borders.empty()
    val threadsViewScrollPane = JBScrollPane(threadsView.component)
    threadsViewScrollPane.border = JBUI.Borders.empty()
    connectionsTab.addTab("Connection View", connectionScrollPane)
    connectionsTab.addTab("Thread View", threadsViewScrollPane)
    if (StudioFlags.ENABLE_NETWORK_INTERCEPTION.get()) {
      connectionsTab.addTab("Rules", rulesView.component)
      var selectedComponent: Component? = null
      connectionsTab.addChangeListener {
        when (connectionsTab.selectedComponent) {
          connectionScrollPane, threadsViewScrollPane ->
            // Switching tabs between connection view and threads view does not open or close details panel.
            if (selectedComponent == rulesView.component) {
              model.detailContent =
                if (model.selectedConnection == null) NetworkInspectorModel.DetailContent.EMPTY
                else NetworkInspectorModel.DetailContent.CONNECTION
            }
          rulesView.component ->
            if (selectedComponent == connectionScrollPane || selectedComponent == threadsViewScrollPane) {
              model.detailContent =
                if (model.selectedRule == null) NetworkInspectorModel.DetailContent.EMPTY
                else NetworkInspectorModel.DetailContent.RULE
            }
        }
        selectedComponent = connectionsTab.selectedComponent
      }
    }
    // The toolbar overlays the tab panel, so we have to make sure we repaint the parent panel when switching tabs.
    connectionsTab.addChangeListener { mainPanel.repaint() }
    connectionsPanel.add(connectionsTab, CARD_CONNECTIONS)
    val infoPanel = JPanel(BorderLayout())
    val infoMessage = InstructionsPanel.Builder(
      TextInstruction(UIUtilities.getFontMetrics(infoPanel, H3_FONT), "Network inspector data unavailable"),
      NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      TextInstruction(UIUtilities.getFontMetrics(infoPanel, STANDARD_FONT),
                      "There is no information for the network traffic you've selected."),
      NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      HyperlinkInstruction(STANDARD_FONT, "Learn More",
                           "https://developer.android.com/r/studio-ui/network-profiler-troubleshoot-connections.html"))
      .setColors(JBColor.foreground(), null)
      .build()
    infoPanel.add(infoMessage, BorderLayout.CENTER)
    infoPanel.name = CARD_INFO
    connectionsPanel.add(infoPanel, CARD_INFO)
    val toolbar = JPanel(GridBagLayout())
    selectionTimeLabel.border = JBUI.Borders.empty(8, 0, 0, 8)
    toolbar.add(selectionTimeLabel)
    mainPanel.add(toolbar, TabularLayout.Constraint(0, 1))
    mainPanel.add(connectionsPanel, TabularLayout.Constraint(0, 0, 2, 2))
    leftSplitter.secondComponent = mainPanel

    model.selectionRangeDataFetcher.addListener(object : SelectionRangeDataListener {
      override fun onUpdate(data: List<HttpData>) {
        val cardLayout = connectionsPanel.layout as CardLayout
        if (data.isEmpty()) {
          val detailedNetworkUsage = model.networkUsage
          if (hasTrafficUsage(detailedNetworkUsage.rxSeries, model.timeline.selectionRange) ||
              hasTrafficUsage(detailedNetworkUsage.txSeries, model.timeline.selectionRange)) {
            cardLayout.show(connectionsPanel, CARD_INFO)
            return
          }
        }
        cardLayout.show(connectionsPanel, CARD_CONNECTIONS)
      }
    })
    val splitter = JBSplitter(false, 0.6f)
    splitter.firstComponent = leftSplitter
    splitter.secondComponent = detailsPanel
    splitter.setHonorComponentsMinimumSize(true)
    splitter.divider.border = DEFAULT_VERTICAL_BORDERS
    component.add(splitter, BorderLayout.CENTER)
  }

  private fun buildTimeAxis(axis: ResizingAxisComponentModel): JComponent {
    val axisPanel = JPanel(BorderLayout())
    axisPanel.background = DEFAULT_BACKGROUND
    val timeAxis = AxisComponent(axis, AxisComponent.AxisOrientation.BOTTOM, true)
    timeAxis.setShowAxisLine(false)
    timeAxis.minimumSize = Dimension(0, TIME_AXIS_HEIGHT)
    timeAxis.preferredSize = Dimension(Int.MAX_VALUE, TIME_AXIS_HEIGHT)
    axisPanel.add(timeAxis, BorderLayout.CENTER)
    return axisPanel
  }

  private fun tooltipChanged() {
    if (activeTooltipView != null) {
      activeTooltipView!!.dispose()
      activeTooltipView = null
    }
    tooltipPanel.removeAll()
    tooltipPanel.isVisible = false
    if (model.tooltip != null) {
      activeTooltipView = tooltipBinder.build(this, model.tooltip)
      tooltipPanel.add(activeTooltipView!!.createComponent())
      tooltipPanel.isVisible = true
    }
    tooltipPanel.invalidate()
    tooltipPanel.repaint()
  }

  private fun selectionChanged() {
    val timeline = model.timeline
    val selectionRange = timeline.selectionRange
    if (selectionRange.isEmpty) {
      selectionTimeLabel.icon = null
      selectionTimeLabel.text = ""
      return
    }

    // Note - relative time conversion happens in nanoseconds
    val selectionMinUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos(selectionRange.min.toLong()))
    val selectionMaxUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos(selectionRange.max.toLong()))
    selectionTimeLabel.icon = StudioIcons.Profiler.Toolbar.CLOCK
    if (selectionRange.isPoint) {
      selectionTimeLabel.text = TimeFormatter.getSimplifiedClockString(selectionMinUs)
    }
    else {
      selectionTimeLabel.text = "${TimeFormatter.getSimplifiedClockString(selectionMinUs)} - ${
        TimeFormatter.getSimplifiedClockString(selectionMaxUs)
      }"
    }
  }

  private fun createSelectionTimeLabel(): JLabel {
    val label = JLabel("")
    label.font = STANDARD_FONT
    label.border = JBUI.Borders.empty(3, 3, 3, 3)
    label.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val timeline = model.timeline
        timeline.frameViewToRange(timeline.selectionRange)
      }
    })
    label.toolTipText = "Selected range"
    label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    return label
  }

  private fun buildMonitorUi(): JPanel {
    val timeline = model.timeline
    val selection = RangeSelectionComponent(model.rangeSelectionModel)
    selection.setCursorSetter(AdtUiUtils::setTooltipCursor)
    val tooltip = RangeTooltipComponent(timeline, tooltipPanel, parentPane) { selection.shouldShowSeekComponent() }
    val layout = TabularLayout("*")
    val panel = JBPanel<Nothing>(layout)
    panel.background = DEFAULT_STAGE_BACKGROUND
    // Order matters, as such we want to put the tooltip component first so we draw the tooltip line on top of all other
    // components.
    panel.add(tooltip, TabularLayout.Constraint(0, 0, 2, 1))

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    val sb = StreamingScrollbar(timeline, panel)
    panel.add(sb, TabularLayout.Constraint(3, 0))
    val viewAxis = ResizingAxisComponentModel.Builder(timeline.viewRange, TimeAxisFormatter.DEFAULT)
      .setGlobalRange(timeline.dataRange).build()
    val timeAxis = buildTimeAxis(viewAxis)
    panel.add(timeAxis, TabularLayout.Constraint(2, 0))
    val monitorPanel = JBPanel<Nothing>(TabularLayout("*", "*"))
    monitorPanel.isOpaque = false
    monitorPanel.border = MONITOR_BORDER
    val label = JLabel(model.name)
    label.border = MONITOR_LABEL_PADDING
    label.verticalAlignment = SwingConstants.TOP
    val lineChartPanel = JBPanel<Nothing>(BorderLayout())
    lineChartPanel.isOpaque = false
    lineChartPanel.border = JBUI.Borders.empty(Y_AXIS_TOP_MARGIN, 0, 0, 0)
    val usage = model.networkUsage
    val lineChart = LineChart(usage)
    val receivedConfig = LineConfig(NETWORK_RECEIVING_COLOR).setLegendIconType(LegendConfig.IconType.LINE)
    lineChart.configure(usage.rxSeries, receivedConfig)
    val sentConfig = LineConfig(NETWORK_SENDING_COLOR).setLegendIconType(LegendConfig.IconType.LINE)
    lineChart.configure(usage.txSeries, sentConfig)
    lineChart.setRenderOffset(0, LineConfig.DEFAULT_DASH_STROKE.lineWidth.toInt() / 2)
    lineChartPanel.add(lineChart, BorderLayout.CENTER)
    val axisPanel = JBPanel<Nothing>(BorderLayout())
    axisPanel.isOpaque = false
    val leftAxis = AxisComponent(model.trafficAxis, AxisComponent.AxisOrientation.RIGHT, true)
    leftAxis.setShowAxisLine(false)
    leftAxis.setShowMax(true)
    leftAxis.setOnlyShowUnitAtMax(true)
    leftAxis.setHideTickAtMin(true)
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN)
    axisPanel.add(leftAxis, BorderLayout.WEST)
    val legends = model.legends
    val legend = LegendComponent.Builder(legends).setRightPadding(LEGEND_RIGHT_PADDING).build()
    legend.configure(legends.rxLegend, LegendConfig(lineChart.getLineConfig(usage.rxSeries)))
    legend.configure(legends.txLegend, LegendConfig(lineChart.getLineConfig(usage.txSeries)))
    val legendPanel = JBPanel<Nothing>(BorderLayout())
    legendPanel.isOpaque = false
    legendPanel.add(label, BorderLayout.WEST)
    legendPanel.add(legend, BorderLayout.EAST)
    model.rangeSelectionModel.addListener(object : RangeSelectionListener {
      override fun selectionCleared() {
        model.setSelectedConnection(null)
      }
    })
    selection.addMouseListener(TooltipMouseAdapter(model) { NetworkTrafficTooltipModel(model) })
    tooltip.registerListenersOn(selection)
    monitorPanel.add(legendPanel, TabularLayout.Constraint(0, 0))
    monitorPanel.add(selection, TabularLayout.Constraint(0, 0))
    monitorPanel.add(axisPanel, TabularLayout.Constraint(0, 0))
    monitorPanel.add(lineChartPanel, TabularLayout.Constraint(0, 0))

    layout.setRowSizing(1, "*") // Give as much space as possible to the main monitor panel
    panel.add(monitorPanel, TabularLayout.Constraint(1, 0))
    return panel
  }

  private fun hasTrafficUsage(series: RangedContinuousSeries, range: Range): Boolean {
    val list = series.getSeriesForRange(range)
    if (list.any { data -> data.x >= range.min && data.x <= range.max && data.value > 0 }) {
      return true
    }

    // If there is no positive value at a time t within given range, check if there is index i that
    // list.get(i).x < range.getMin <= range.getMax < list.get(i + 1).x; and values at i and i+1 are positive.
    val getInsertPoint: (Long) -> Int = { time ->
      val index = Collections.binarySearch(list, SeriesData(time, 0L)) { o1, o2 ->
        o1.x.compareTo(o2.x)
      }
      if (index < 0) -(index + 1) else index
    }
    val minIndex = getInsertPoint(range.min.toLong())
    val maxIndex = getInsertPoint(range.max.toLong())
    return if (minIndex == maxIndex) {
      minIndex > 0 && list[minIndex - 1].value > 0 && minIndex < list.size && list[minIndex].value > 0
    }
    else false
  }
}