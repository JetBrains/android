/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.insights.model.issue.SignalType
import com.android.tools.idea.insights.model.issue.SignalType.SIGNAL_EARLY
import com.android.tools.idea.insights.model.issue.SignalType.SIGNAL_FRESH
import com.android.tools.idea.insights.model.issue.SignalType.SIGNAL_REGRESSED
import com.android.tools.idea.insights.model.issue.SignalType.SIGNAL_REPETITIVE
import com.android.tools.idea.insights.model.issue.SignalType.SIGNAL_UNSPECIFIED
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import icons.StudioIcons
import javax.swing.Icon

fun SignalType.toLogProto() =
  when (this) {
    SIGNAL_EARLY ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.EARLY_SIGNAL
    SIGNAL_FRESH ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.FRESH_SIGNAL
    SIGNAL_REGRESSED ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.REGRESSIVE_SIGNAL
    SIGNAL_REPETITIVE ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.REPETITIVE_SIGNAL
    SIGNAL_UNSPECIFIED ->
      AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.UNKNOWN_SIGNAL
  }

val SignalType.icon: Icon?
  get() =
    when (this) {
      SIGNAL_UNSPECIFIED -> null
      SIGNAL_EARLY -> StudioIcons.AppQualityInsights.EARLY_SIGNAL
      SIGNAL_FRESH -> StudioIcons.AppQualityInsights.FRESH_SIGNAL
      SIGNAL_REGRESSED -> StudioIcons.AppQualityInsights.REGRESSED_SIGNAL
      SIGNAL_REPETITIVE -> StudioIcons.AppQualityInsights.REPETITIVE_SIGNAL
    }
