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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuThreadTrackModel
import kotlin.streams.toList

/**
 * Base model class for the events tab.
 *
 * @param <T> type of the data to select events from.</T>
 */
abstract class CpuAnalysisEventsTabModel<T>(val captureRange: Range) : CpuAnalysisTabModel<T>(Type.EVENTS) {
  /**
   * @return list of nodes to put in the table
   */
  abstract fun getNodes(): List<CaptureNode>
}

/**
 * Events tab model for threads.
 */
class CpuThreadAnalysisEventsTabModel(captureRange: Range) : CpuAnalysisEventsTabModel<CpuThreadTrackModel>(captureRange) {

  /**
   * @return all nodes in this thread.
   */
  override fun getNodes() = dataSeries
    .mapNotNull { it.callChartModel.node }
    .flatMap {
      it.descendantsStream.filter { node ->
        node.depth > 0
      }.toList()
    }
}

/**
 * Events tab model for capture node.
 */
class CaptureNodeAnalysisEventsTabModel(captureRange: Range) : CpuAnalysisEventsTabModel<CaptureNodeAnalysisModel>(captureRange) {

  /**
   * @return all occurrences of this node in the current thread.
   */
  override fun getNodes() = dataSeries.flatMap {
    it.node.findRootNode().descendantsStream.filter { child ->
      child.data.fullName == it.node.data.fullName
    }.toList()
  }
}