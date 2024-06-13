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

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

private val scheduler =
  AppExecutorUtil.createBoundedScheduledExecutorService(
    "App Insights Performance Statistics Collector",
    1,
  )

/**
 * A manager for tracking performance/latency sensitive executions in AQI.
 *
 * We will report on hourly basis and at application shutdown. That is, events will be recorded in
 * recorders first, and then histograms of value measurements will be sent out at the scheduled
 * time.
 */
@Service(Service.Level.APP)
class AppInsightsPerformanceTracker : Disposable {
  init {
    // schedule hourly logging
    scheduler.scheduleWithFixedDelay({ reportPerformanceStats() }, 1, 1, TimeUnit.HOURS)
  }

  private val vcBasedLineNumberMappingLatencyRecorder =
    VersionControlBasedLineNumberMappingLatencyRecorder()

  fun recordVersionControlBasedLineNumberMappingLatency(latencyMs: Long) {
    vcBasedLineNumberMappingLatencyRecorder.recordLatency(latencyMs)
  }

  private fun reportPerformanceStats() {
    val performanceStats =
      AppQualityInsightsUsageEvent.PerformanceStats.newBuilder()
        .apply {
          vcBasedLineNumberMappingLatencyRecorder.reportLatency()?.let {
            vcBasedLineNumberMappingLatency = it
          }
        }
        .build()
        .takeUnless { it == AppQualityInsightsUsageEvent.PerformanceStats.getDefaultInstance() }
        ?: return

    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.PERFORMANCE_STATS
            setPerformanceStats(performanceStats)
          }
        )
    )
  }

  private fun generateAndroidStudioEventBuilder(): AndroidStudioEvent.Builder {
    return AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.FIREBASE_ASSISTANT)
      .setKind(AndroidStudioEvent.EventKind.APP_QUALITY_INSIGHTS_USAGE)
  }

  override fun dispose() {
    reportPerformanceStats()
  }
}
