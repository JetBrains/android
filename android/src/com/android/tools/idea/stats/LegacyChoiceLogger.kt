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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UserSentiment
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.VERY_SATISFIED_VALUE
import com.google.wireless.android.sdk.stats.SentimentSurveyEvent

object LegacyChoiceLogger : ChoiceLogger {
  override fun log(name: String, result: Int) {
    val level = UserSentiment.SatisfactionLevel.entries
                  .firstOrNull { it.number == VERY_SATISFIED_VALUE - result }
                ?: UNKNOWN_SATISFACTION_LEVEL

    logEvents(level, SentimentSurveyEvent.Type.TYPE_INVOKED)
  }

  override fun log(name: String, result: List<Int>) {
    result.firstOrNull()?.let {
      log(name, it)
    }
  }

  override fun cancel(name: String) {
    logEvents(UNKNOWN_SATISFACTION_LEVEL, SentimentSurveyEvent.Type.TYPE_CANCELLED)
  }

  private fun logEvents(level: UserSentiment.SatisfactionLevel, type: SentimentSurveyEvent.Type) {
    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.USER_SENTIMENT
      userSentiment = UserSentiment.newBuilder().apply {
        state = UserSentiment.SentimentState.POPUP_QUESTION
        this.level = level
      }.build()
    })

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.SENTIMENT_SURVEY_EVENT
      sentimentSurveyEvent = SentimentSurveyEvent.newBuilder().apply {
        this.type = type
        surveyType = SentimentSurveyEvent.SurveyType.SURVEY_TYPE_IN_PRODUCT
      }.build()
    })
  }
}