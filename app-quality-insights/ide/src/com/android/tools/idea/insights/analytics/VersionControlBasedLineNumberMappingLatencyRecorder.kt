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
package com.android.tools.idea.insights.analytics

import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import org.HdrHistogram.SingleWriterRecorder

/**
 * This is to record the latency of version control based line number mapping happens in
 * [AppInsightsExternalAnnotator.doAnnotate].
 *
 * The total amount of latency has 2 parts:
 * 1) time to take for retrieving historical content
 * 2) time to do full diff between the historical file and the current source file
 *
 * Part (1) above might take time if no previously cached contents.
 */
class VersionControlBasedLineNumberMappingLatencyRecorder {
  private val recorder = SingleWriterRecorder(1)

  fun recordLatency(latencyMs: Long) {
    recorder.recordValue(latencyMs)
  }

  /**
   * Returns
   * [AppQualityInsightsUsageEvent.PerformanceStats.VersionControlBasedLineNumberMappingLatency] or
   * null if nothing to report.
   */
  fun reportLatency():
    AppQualityInsightsUsageEvent.PerformanceStats.VersionControlBasedLineNumberMappingLatency? {
    val histogram = recorder.intervalHistogram.takeIf { it.totalCount != 0L } ?: return null

    return AppQualityInsightsUsageEvent.PerformanceStats.VersionControlBasedLineNumberMappingLatency
      .newBuilder()
      .apply {
        minLatencyMs = histogram.minValue
        p50LatencyMs = histogram.getValueAtPercentile(50.0)
        p90LatencyMs = histogram.getValueAtPercentile(90.0)
        maxLatencyMs = histogram.maxValue
      }
      .build()
  }
}
