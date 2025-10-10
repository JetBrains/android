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
package com.android.tools.idea.stats

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.analytics.UsageTrackerWriter
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SurveyResponse
import com.google.wireless.android.sdk.stats.UserSentiment
import org.junit.After
import org.junit.Before
import org.junit.Test

class ChoiceLoggerTest {
  lateinit var usageTrackerWriter: TestUsageTracker
  lateinit var oldUsageTrackerWriter: UsageTrackerWriter<AndroidStudioEvent.Builder>

  @Before
  fun setUp() {
    usageTrackerWriter = TestUsageTracker(VirtualTimeScheduler())
    oldUsageTrackerWriter = setWriterForTest(usageTrackerWriter)
  }

  @After
  fun tearDown() {
    setWriterForTest(oldUsageTrackerWriter)
  }

  @Test
  fun logSingle() {
    ChoiceLoggerImpl.log("test", 1)
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.surveyResponse).isEqualTo(SurveyResponse.newBuilder().apply {
      name = "test"
      addAllResponses(listOf(1))
    }.build()
    )
  }

  @Test
  fun logMultiple() {
    ChoiceLoggerImpl.log("test", listOf(1, 2))
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.surveyResponse).isEqualTo(SurveyResponse.newBuilder().apply {
      name = "test"
      addAllResponses(listOf(1, 2))
    }.build()
    )
  }

  @Test
  fun cancel() {
    ChoiceLoggerImpl.cancel("test")
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.surveyResponse).isEqualTo(SurveyResponse.newBuilder().apply {
      name = "test"
    }.build()
    )
  }

  @Test
  fun legacy_logSingle() {
    LegacyChoiceLogger.log("test", 1)
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.userSentiment).isEqualTo(UserSentiment.newBuilder().apply {
      state = UserSentiment.SentimentState.POPUP_QUESTION
      level = UserSentiment.SatisfactionLevel.SATISFIED
    }.build()
    )
  }

  @Test
  fun legacy_logSingle_invalid() {
    LegacyChoiceLogger.log("test", -1)
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.userSentiment).isEqualTo(UserSentiment.newBuilder().apply {
      state = UserSentiment.SentimentState.POPUP_QUESTION
      level = UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL
    }.build()
    )
  }

  @Test
  fun legacy_logMultiple_onlyFirstScoreIsUsed() {
    LegacyChoiceLogger.log("test", listOf(1, 2))
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.userSentiment).isEqualTo(UserSentiment.newBuilder().apply {
      state = UserSentiment.SentimentState.POPUP_QUESTION
      level = UserSentiment.SatisfactionLevel.SATISFIED
    }.build()
    )
  }

  @Test
  fun legacy_cancel() {
    LegacyChoiceLogger.cancel("test")
    assertThat(usageTrackerWriter.usages).hasSize(1)
    assertThat(usageTrackerWriter.usages[0].studioEvent.userSentiment).isEqualTo(UserSentiment.newBuilder().apply {
      state = UserSentiment.SentimentState.POPUP_QUESTION
      level = UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL
    }.build()
    )
  }
}
