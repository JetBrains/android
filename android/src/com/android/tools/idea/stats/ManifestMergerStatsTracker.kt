/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.toProto
import com.android.utils.mapValuesNotNull
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ManifestMergerStats
import com.intellij.openapi.application.ApplicationManager
import org.HdrHistogram.SingleWriterRecorder
import java.util.concurrent.ConcurrentHashMap

/** Object used to record metrics related to running the Manifest Merger within the IDE. */
object ManifestMergerStatsTracker {

  enum class MergeResult {
    CANCELED, FAILED, SUCCESS
  }

  private val histogramsByResult = ConcurrentHashMap<MergeResult, SingleWriterRecorder>()

  /** Store a manifest merger run time for eventual analytics reporting. */
  fun recordManifestMergeRunTime(runTimeMs: Long, mergeResult: MergeResult) {
    ApplicationManager.getApplication().invokeLater {
      // This runs on the EDT to ensure a single writer, but is thread-safe with respect to a background thread reading the histogram.
      histogramsByResult.computeIfAbsent(mergeResult) { SingleWriterRecorder(1) }.recordValue(runTimeMs)
    }
  }

  /** Trigger reporting of all existing manifest merger run times. */
  fun reportMergerStats() {
    val statsBuilder = ManifestMergerStats.newBuilder()

    val histogramsWithValues = histogramsByResult.mapValuesNotNull {
      it.value.intervalHistogram?.takeIf { histogram -> histogram.totalCount > 0L }?.toProto()
    }

    if (histogramsWithValues.isEmpty()) return

    for ((mergeResult, histogram) in histogramsWithValues) {
      when (mergeResult) {
        MergeResult.SUCCESS -> statsBuilder.successRunTimeMs = histogram
        MergeResult.CANCELED -> statsBuilder.canceledRunTimeMs = histogram
        MergeResult.FAILED -> statsBuilder.failedRunTimeMs = histogram
      }
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.MANIFEST_MERGER_STATS
      manifestMergerStats = statsBuilder.build()
    })
  }
}
