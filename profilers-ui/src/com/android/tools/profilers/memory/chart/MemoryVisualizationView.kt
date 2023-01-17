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
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerCombobox
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.CapturePanelTabContainer
import com.android.tools.profilers.memory.CaptureSelectionAspect
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.MemoryCaptureSelection
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject
import com.android.tools.profilers.memory.adapters.classifiers.NativeCallStackSet
import com.android.tools.profilers.memory.chart.MemoryVisualizationModel.XAxisFilter
import com.google.common.base.Strings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
class MemoryVisualizationView(private val selection: MemoryCaptureSelection,
                              private val profilersView: StudioProfilersView) : AspectObserver(), CapturePanelTabContainer {
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
  }

  val toolbarComponents: List<Component>
    get() {
      val components: MutableList<Component> = ArrayList()
      components.add(orderingDropdown)
      return components
    }

  override fun onSelectionChanged(selected: Boolean) {
    if (selected) {
      selection.aspect.addDependency(this).onChange(CaptureSelectionAspect.CURRENT_FILTER) { rebuildUI() }
      initialClassGrouping = selection.selectedHeapSet?.classGrouping ?: return
      // Rebuilding the UI also sets the selected heap to a callstack view.
      rebuildUI()
      // After the heap is setup properly and our UI elements are created select the current heap to apply
      // any filters previously set from other tabs.
      selection.filterHandler.refreshFilterContent()
    }
    else {
      selection.aspect.removeDependencies(this)
      selection.selectedHeapSet?.classGrouping = initialClassGrouping ?: return
      // After the heap is setup properly back to its initial state select the heap to apply filters previously set from this tab.
      selection.filterHandler.refreshFilterContent()
    }
  }

  private fun rebuildUI() {
    // This is not CPU efficient however it is memory efficient. To make this CPU efficient a copy of the HeapSet would need to be
    // maintained for the Visualization view. Instead of managing a duplicate copy of the HeapSet when the visualization tab is activated,
    // the class grouping is updated. This update forces a rebuild of the model in a hierarchical way as is expected by the HTreeChart.
    selection.selectedHeapSet?.classGrouping = if (selection.selectedCapture is NativeAllocationSampleCaptureObject)
      ClassGrouping.NATIVE_ARRANGE_BY_CALLSTACK
    else ClassGrouping.ARRANGE_BY_CALLSTACK
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
    val globalRange = Range(captureRange)
    // We use selectionRange here instead of nodeRange, because nodeRange synchronises with selectionRange and vice versa.
    // In other words, there is a constant ratio between them. And the horizontal scrollbar represents selection range within
    // capture range.
    val horizontalScrollBar = RangeTimeScrollBar(globalRange, captureRange, TimeUnit.MICROSECONDS)
    horizontalScrollBar.preferredSize = Dimension(horizontalScrollBar.preferredSize.width, 10)
    val axis = createAxis(model.formatter(), captureRange)
    val chart = createChart(selected, captureRange, globalRange)
    chart.addMouseMotionListener(
      MemoryVisualizationTooltipView(chart, profilersView.component, VisualizationTooltipModel(captureRange, model)))
    val panel = JPanel(TabularLayout("*,Fit", "*,Fit"))
    panel.add(axis, TabularLayout.Constraint(0, 0))
    panel.add(chart, TabularLayout.Constraint(0, 0))
    panel.add(HTreeChartVerticalScrollBar(chart), TabularLayout.Constraint(0, 1))
    panel.add(horizontalScrollBar, TabularLayout.Constraint(1, 0, 1, 2))
    val navigator = profilersView.studioProfilers.ideServices.codeNavigator
    profilersView.ideProfilerComponents.createContextMenuInstaller()
      .installNavigationContextMenu(chart, navigator) { getCodeLocation(chart) }
    return panel
  }

  private fun getCodeLocation(chart: HTreeChart<ClassifierSetHNode>): CodeLocation? {
    val nativeSet = chart.focusedNode?.data
    if (nativeSet is NativeCallStackSet) {
      if (!Strings.isNullOrEmpty(nativeSet.fileName)) {
        return CodeLocation.Builder(nativeSet.name) // Expects class name but we don't have that so we use the function.
          .setMethodName(nativeSet.name)
          .setFileName(nativeSet.fileName)
          .setLineNumber(nativeSet.lineNumber - 1) // Line numbers from symbolizer are 1 based UI is 0 based.
          .build()
      }
    }
    return null
  }

  private fun createAxis(formatter: BaseAxisFormatter,
                         range: Range): AxisComponent {
    val axisModel: AxisComponentModel = ResizingAxisComponentModel.Builder(range, formatter).build()
    val axis = AxisComponent(axisModel, AxisComponent.AxisOrientation.BOTTOM, true)
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

  private fun createChart(node: ClassifierSetHNode, range: Range, globalRange: Range): HTreeChart<ClassifierSetHNode> {
    val orientation = HTreeChart.Orientation.TOP_DOWN
    return HTreeChart.Builder<ClassifierSetHNode>(node, range, HeapSetNodeHRenderer())
      // Create a new Range for the global range. This allows the global range to remain fixed while the view range updates.
      .setGlobalXRange(globalRange)
      .setOrientation(orientation)
      .setRootVisible(false)
      .build()
      .apply {
        isDrawDebugInfo = profilersView.studioProfilers.ideServices.featureConfig.isPerformanceMonitoringEnabled
      }
  }
}