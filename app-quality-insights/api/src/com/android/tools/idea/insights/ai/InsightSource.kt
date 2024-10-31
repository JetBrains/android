/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ai

import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AiInsightSource

enum class InsightSource {
  UNKNOWN,
  STUDIO_BOT,
  CRASHLYTICS_TITAN;

  fun toProto(): AiInsightSource =
    when (this) {
      UNKNOWN -> AiInsightSource.UNKNOWN_SOURCE
      STUDIO_BOT -> AiInsightSource.AI_INSIGHT_SOURCE_STUDIO_BOT
      CRASHLYTICS_TITAN -> AiInsightSource.AI_INSIGHT_SOURCE_CRASHLYTICS_TITAN
    }
}
