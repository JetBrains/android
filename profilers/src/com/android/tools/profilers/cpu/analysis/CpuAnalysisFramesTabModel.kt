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

import com.android.tools.adtui.model.AbstractPaginatedTableModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuCapture
import java.util.concurrent.TimeUnit
import javax.swing.RowSorter
import javax.swing.SortOrder

/**
 * Tab model for the Frames tab in the Analysis Panel.
 */
class CpuAnalysisFramesTabModel(val captureRange: Range) : CpuAnalysisTabModel<CpuCapture>(Type.FRAMES) {
  /**
   * Frame table data by layer. This is lazily initialized because at the time of construction, the [CpuCapture] data is not yet available.
   */
  val layerToTableModel: Map<String, FrameEventTableModel> by lazy {
    val layers = dataSeries.flatMap { it.systemTraceData?.getAndroidFrameLayers() ?: listOf() }
    // Transform frame event proto (grouped by phase) into table model (grouped by frame number).
    layers.associate { layer ->
      val frameNumberToRow = mutableMapOf<Int, FrameEventRow>().also {
        layer.phaseList.forEach { phase ->
          phase.frameEventList.forEach { frameEvent ->
            val frameEventRow = it.getOrPut(frameEvent.frameNumber) {
              // Initialize start and end timestamps with capture range for the incomplete edge frames.
              FrameEventRow(frameEvent.frameNumber, startTimeUs = captureRange.min.toLong(), endTimeUs = captureRange.max.toLong())
            }
            val timestampUs = TimeUnit.NANOSECONDS.toMicros(frameEvent.timestampNanoseconds)
            val durationUs = TimeUnit.NANOSECONDS.toMicros(frameEvent.durationNanoseconds)
            when (phase.phaseName) {
              "App" -> {
                frameEventRow.startTimeUs = timestampUs
                frameEventRow.appDurationUs = durationUs
              }
              "GPU" -> frameEventRow.gpuDurationUs = durationUs
              "Composition" -> frameEventRow.compositionDurationUs = durationUs
              "Display" -> frameEventRow.endTimeUs = timestampUs
            }
          }
        }
      }
      Pair(layer.layerName, FrameEventTableModel(frameNumberToRow.values.toMutableList()))
    }
  }
}

/**
 * Data class to represent a single row in the frame events table.
 */
data class FrameEventRow(val frameNumber: Int,
                         var startTimeUs: Long,
                         var endTimeUs: Long,
                         var appDurationUs: Long = 0L,
                         var gpuDurationUs: Long = 0L,
                         var compositionDurationUs: Long = 0L) {
  val totalDurationUs: Long
    get() = endTimeUs - startTimeUs
}

/**
 * Table model for the frame events table.
 */
class FrameEventTableModel(val frameEvents: MutableList<FrameEventRow>) : AbstractPaginatedTableModel(25) {
  override fun getDataSize(): Int {
    return frameEvents.size
  }

  override fun getDataValueAt(dataIndex: Int, columnIndex: Int): Any {
    return FrameEventTableColumn.values()[columnIndex].getValueFrom(frameEvents[dataIndex])
  }

  override fun sortData(sortKeys: List<RowSorter.SortKey>) {
    if (sortKeys.isNotEmpty()) {
      val sortKey = sortKeys[0]
      val comparator = FrameEventTableColumn.values()[sortKey.column].getComparator()
      if (sortKey.sortOrder == SortOrder.ASCENDING) {
        frameEvents.sortWith(comparator)
      }
      else {
        frameEvents.sortWith(comparator.reversed())
      }
    }
  }

  override fun getColumnCount(): Int {
    return FrameEventTableColumn.values().size
  }

  override fun getColumnClass(columnIndex: Int): Class<*> {
    return FrameEventTableColumn.values()[columnIndex].type
  }

  override fun getColumnName(column: Int): String {
    return FrameEventTableColumn.values()[column].displayName
  }
}

/**
 * Column definition for the frame events table.
 *
 * @param type use Java number classes (e.g. [java.lang.Long]) to ensure proper sorting in JTable
 */
enum class FrameEventTableColumn(val displayName: String, val type: Class<*>, val getValueFrom: (FrameEventRow) -> Comparable<*>) {
  FRAME_NUMBER("Frame #", java.lang.Integer::class.java, FrameEventRow::frameNumber),
  TOTAL_TIME("Total Time", java.lang.Long::class.java, FrameEventRow::totalDurationUs),
  APP("Application", java.lang.Long::class.java, FrameEventRow::appDurationUs),
  GPU("Wait for GPU", java.lang.Long::class.java, FrameEventRow::gpuDurationUs),
  COMPOSITION("Composition", java.lang.Long::class.java, FrameEventRow::compositionDurationUs);

  fun getComparator(): Comparator<FrameEventRow> = compareBy(getValueFrom)
}