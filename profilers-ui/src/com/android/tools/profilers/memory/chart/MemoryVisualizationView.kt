/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.RangeTimeScrollBar
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.chart.hchart.HTreeChartVerticalScrollBar
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.BaseAxisFormatter
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerCombobox
import com.android.tools.profilers.memory.CapturePanelTabContainer
import com.android.tools.profilers.memory.CaptureSelectionAspect
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.MemoryCaptureSelection
import com.android.tools.profilers.memory.MemoryProfilerAspect
import com.android.tools.profilers.memory.MemoryProfilerStage
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject
import com.android.tools.profilers.memory.chart.MemoryVisualizationModel.XAxisFilter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Class that manages the memory HTreeChart (CallChart). The UI has a dropdown that allows the user to change the X axis of the
 * chart. This class is responsible for rebuilding the chart when the dropdown changes or a filter is applied.
 */
class MemoryVisualizationView(private val selection: MemoryCaptureSelection) : AspectObserver(), CapturePanelTabContainer {
  private val panel: JPanel = JPanel(BorderLayout())
  private val orderingDropdown: JComboBox<XAxisFilter> = ProfilerCombobox()
  private val model = MemoryVisualizationModel()
  private var initialClassGrouping: ClassGrouping? = null

  init {
    orderingDropdown.addActionListener { e: ActionEvent? ->
      val item = orderingDropdown.selectedItem
      if (item is XAxisFilter) {
        model.axisFilter = item
        rebuildUI()
      }
    }
    val comboBoxModel: ComboBoxModel<XAxisFilter> = DefaultComboBoxModel(XAxisFilter.values())
    orderingDropdown.model = comboBoxModel
    orderingDropdown.selectedIndex = XAxisFilter.ALLOC_SIZE.ordinal
    selection.aspect.addDependency(this).onChange(CaptureSelectionAspect.CURRENT_FILTER) { rebuildUI() }
  }

  val toolbarComponents: List<Component>
    get() {
      val components: MutableList<Component> = ArrayList()
      components.add(orderingDropdown)
      return components
    }

  override fun onSelectionChanged(selected: Boolean) {
    if (selected) {
      initialClassGrouping = selection.selectedHeapSet?.classGrouping ?: return
      rebuildUI()
    }
    else {
      selection.selectedHeapSet?.classGrouping = initialClassGrouping ?: return
    }
  }

  private fun rebuildUI() {
    // This is not CPU efficient however it is memory efficient. To make this CPU efficient a copy of the HeapSet would need to be
    // maintained for the Visualization view. Instead of managing a duplicate copy of the HeapSet when the visualization tab is activated,
    // the class grouping is updated. This update forces a rebuild of the model in a hierarchical way as is expected by the HTreeChart.
    selection.selectedHeapSet?.classGrouping = if (selection.selectedCapture is NativeAllocationSampleCaptureObject)
      ClassGrouping.NATIVE_ARRANGE_BY_CALLSTACK else ClassGrouping.ARRANGE_BY_CALLSTACK
    panel.removeAll()
    panel.add(createChartPanel(), BorderLayout.CENTER)
  }

  override val component: JComponent
    get() = panel

  private fun createChartPanel(): JPanel {
    // If we don't have a heap selected return an empty panel.
    selection.selectedHeapSet ?: return JPanel()

    val selected = ClassifierSetHNode(model, selection.selectedHeapSet!!, 0)
    selected.updateChildrenOffsets()
    val captureRange = Range(0.0, selected.end.toDouble())
    // We use selectionRange here instead of nodeRange, because nodeRange synchronises with selectionRange and vice versa.
    // In other words, there is a constant ratio between them. And the horizontal scrollbar represents selection range within
    // capture range.
    val horizontalScrollBar = RangeTimeScrollBar(captureRange, captureRange, TimeUnit.MICROSECONDS)
    horizontalScrollBar.preferredSize = Dimension(horizontalScrollBar.preferredSize.width, 10)
    val axis = createAxis(
      XAxisFilter.values()[orderingDropdown.selectedIndex], captureRange, captureRange)
    val chart = createChart(selected, captureRange)
    val panel = JPanel(TabularLayout("*,Fit", "*,Fit"))
    panel.add(axis, TabularLayout.Constraint(0, 0))
    panel.add(chart, TabularLayout.Constraint(0, 0))
    panel.add(HTreeChartVerticalScrollBar(chart), TabularLayout.Constraint(0, 1))
    panel.add(horizontalScrollBar, TabularLayout.Constraint(1, 0, 1, 2))
    return panel
  }

  private fun createAxis(filter: XAxisFilter,
                         range: Range,
                         globalRange: Range): AxisComponent {
    val formatter: BaseAxisFormatter
    formatter = if (filter == XAxisFilter.ALLOC_SIZE || filter == XAxisFilter.TOTAL_SIZE) {
      MemoryAxisFormatter(1, 10, 1)
    }
    else {
      SingleUnitAxisFormatter(1, 10, 1, "")
    }
    val axisModel: AxisComponentModel = ResizingAxisComponentModel.Builder(range, formatter).setGlobalRange(globalRange).build()
    val axis = AxisComponent(axisModel, AxisComponent.AxisOrientation.BOTTOM)
    axis.setShowAxisLine(false)
    axis.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR)
    axis.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        axis.setMarkerLengths(axis.height, 0)
        axis.repaint()
      }
    })
    return axis
  }

  private fun createChart(node: ClassifierSetHNode, range: Range): HTreeChart<ClassifierSetHNode> {
    val orientation = HTreeChart.Orientation.TOP_DOWN
    return HTreeChart.Builder<ClassifierSetHNode>(node, range, HeapSetNodeHRenderer())
      .setGlobalXRange(range)
      .setOrientation(orientation)
      .setRootVisible(false)
      .build()
  }
}