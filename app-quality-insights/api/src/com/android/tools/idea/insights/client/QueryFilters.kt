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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.Version
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class Interval(val startTime: Instant, val endTime: Instant) {
  // This function converts the analog duration to what is assumed to be the filter that caused
  // them.
  // Because the duration may not be exact due to time passing,
  fun toTimeFilter(): AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter {
    val duration = this.duration
    if (duration >= Duration.of(85, ChronoUnit.DAYS)) {
      return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.NINETY_DAYS
    }
    if (duration >= Duration.of(55, ChronoUnit.DAYS)) {
      return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.SIXTY_DAYS
    }
    if (duration >= Duration.of(25, ChronoUnit.DAYS)) {
      return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.THIRTY_DAYS
    }
    if (duration >= Duration.of(6, ChronoUnit.DAYS)) {
      return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.SEVEN_DAYS
    }
    if (duration >= Duration.of(20, ChronoUnit.HOURS)) {
      return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.TWENTYFOUR_HOURS
    }
    return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.TimeFilter.UNKNOWN_FILTER
  }

  val duration: Duration
    get() = Duration.between(startTime, endTime)
}

/** Filtering options for queries. */
data class QueryFilters(
  val interval: Interval,
  val versions: Set<Version> = setOf(Version.ALL),
  val devices: Set<Device> = setOf(Device.ALL),
  val operatingSystems: Set<OperatingSystemInfo> = setOf(OperatingSystemInfo.ALL),
  val eventTypes: List<FailureType> = emptyList(),
  val signal: SignalType = SignalType.SIGNAL_UNSPECIFIED
)
