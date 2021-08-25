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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.PaginatedTableView
import com.android.tools.profilers.StudioProfilersView
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class CpuAnalysisFramesTab(profilersView: StudioProfilersView,
                           model: CpuAnalysisFramesTabModel
) : CpuAnalysisTab<CpuAnalysisFramesTabModel>(profilersView, model) {
  init {
    layout = BorderLayout()
    val tableContainer = JPanel(BorderLayout())
    fun initializeTable(model: FrameEventTableModel) {
      val table = PaginatedTableView(model, PAGE_SIZE_VALUES).apply {
        table.apply {
          showVerticalLines = true
          showHorizontalLines = true
          emptyText.text = "No frames in the selected range"
          columnModel.getColumn(FrameEventTableColumn.FRAME_NUMBER.ordinal).cellRenderer = CustomBorderTableCellRenderer()
          columnModel.getColumn(FrameEventTableColumn.TOTAL_TIME.ordinal).cellRenderer = DurationRenderer()
          columnModel.getColumn(FrameEventTableColumn.APP.ordinal).cellRenderer = DurationRenderer()
          columnModel.getColumn(FrameEventTableColumn.GPU.ordinal).cellRenderer = DurationRenderer()
          columnModel.getColumn(FrameEventTableColumn.COMPOSITION.ordinal).cellRenderer = DurationRenderer()

          setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
          selectionModel.addListSelectionListener {
            if (selectedRow >= 0) {
              val selectedEventIndex = convertRowIndexToModel(selectedRow) + model.pageIndex * model.pageSize
              model.frameEvents[selectedEventIndex].let {
                profilersView.studioProfilers.stage.timeline.viewRange.set(it.startTimeUs.toDouble(), it.endTimeUs.toDouble())
              }
            }
          }
        }
      }
      tableContainer.removeAll()
      tableContainer.add(table.component)
    }

    if (model.tableModels.size > 1) {
      // Add a dropdown list when there are multiple layers.
      val layerDropdownList = ComboBox(model.tableModels.toTypedArray()).apply {
        addActionListener {
          initializeTable(this.selectedItem as FrameEventTableModel)
        }
      }
      add(JPanel(BorderLayout()).apply {
        border = JBUI.Borders.emptyLeft(8)
        add(JBLabel("Select layer: "), BorderLayout.WEST)
        add(layerDropdownList, BorderLayout.CENTER)
      }, BorderLayout.NORTH)
    }
    initializeTable(model.tableModels[0])
    add(tableContainer, BorderLayout.CENTER)
  }

  companion object {
    val PAGE_SIZE_VALUES = arrayOf(10, 25, 50, 100)
  }
}