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
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType

/**
 * Analysis tab model for capture nodes.
 */
class CaptureNodeAnalysisSummaryTabModel(captureRange: Range, private val traceType: TraceType) :
           CpuAnalysisSummaryTabModel<CaptureNodeAnalysisModel>(captureRange) {
  override fun getLabel() = when (traceType) {
    TraceType.ATRACE, TraceType.PERFETTO -> "Trace Event"
    TraceType.ART, TraceType.SIMPLEPERF -> "Stack Frame"
    else -> "Stack Frame"
  }

  // Find the smallest min and largest max of all nodes and return that as the selection range of multiple nodes.
  override fun getSelectionRange() = Range(dataSeries.minOfOrNull { it.nodeRange.min } ?: Double.MAX_VALUE,
                                           dataSeries.maxOfOrNull { it.nodeRange.max } ?: -Double.MAX_VALUE)
}