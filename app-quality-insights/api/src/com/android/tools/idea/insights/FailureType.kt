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
package com.android.tools.idea.insights

import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.diagnostic.Logger
import icons.StudioIcons
import javax.swing.Icon

enum class FailureType {
  UNSPECIFIED,
  FATAL,
  NON_FATAL,
  ANR,
  USER_PERCEIVED_ONLY,
  FOREGROUND,
  BACKGROUND;

  fun getIcon(): Icon? =
    when (this) {
      FATAL -> StudioIcons.AppQualityInsights.FATAL
      NON_FATAL -> StudioIcons.AppQualityInsights.NON_FATAL
      ANR -> StudioIcons.AppQualityInsights.ANR
      USER_PERCEIVED_ONLY,
      FOREGROUND,
      BACKGROUND -> null // TODO: add icons
      // This scenario shouldn't ever be reached.
      UNSPECIFIED -> null
    }

  fun toCrashType(): AppQualityInsightsUsageEvent.CrashType =
    when (this) {
      UNSPECIFIED -> AppQualityInsightsUsageEvent.CrashType.UNKNOWN_TYPE
      FATAL -> AppQualityInsightsUsageEvent.CrashType.FATAL
      NON_FATAL -> AppQualityInsightsUsageEvent.CrashType.NON_FATAL
      ANR -> AppQualityInsightsUsageEvent.CrashType.UNKNOWN_TYPE
      else -> {
        Logger.getInstance(FailureType::class.java)
          .warn("Unrecognized app insights usage event crash type: $this")
        AppQualityInsightsUsageEvent.CrashType.UNKNOWN_TYPE
      }
    }
}

fun convertSeverityList(
  fatalities: List<FailureType>
): AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter {
  if (fatalities.size < 1 || fatalities.size > 2) {
    return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter
      .UNKNOWN_SEVERITY
  }
  if (fatalities.size == 2) {
    return AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.ALL
  }
  return when (fatalities[0]) {
    FailureType.ANR ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.UNKNOWN_SEVERITY
    FailureType.FATAL ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.FATAL
    FailureType.NON_FATAL ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.NON_FATAL
    FailureType.UNSPECIFIED ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.UNKNOWN_SEVERITY
    else ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SeverityFilter.UNKNOWN_SEVERITY
  }
}
