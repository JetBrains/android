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
package com.android.tools.idea.insights.events

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.FakeInsightsProvider
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.InsightSentiment.Sentiment
import java.time.Instant
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class InsightFeedbackSubmittedTest {
  @Test
  fun `InsightFeedbackSubmitted tracks feedback and caches it`() {
    val tracker = mock<AppInsightsTracker>()
    val cache = AppInsightsCacheImpl()
    val startingState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
        currentInsight = LoadingState.Ready(DEFAULT_AI_INSIGHT),
      )

    val transition =
      InsightFeedbackSubmitted(InsightFeedback.THUMBS_UP)
        .transition(startingState, tracker, FakeInsightsProvider(), cache)

    val expectedInsight = DEFAULT_AI_INSIGHT.copy(feedback = InsightFeedback.THUMBS_UP)
    assertThat(transition.newState.currentInsight.valueOrNull()).isEqualTo(expectedInsight)
    assertThat(cache.getAiInsight(CONNECTION1, ISSUE1.id, null, Experiment.UNKNOWN))
      .isEqualTo(expectedInsight.copy(isCached = true))

    verify(tracker)
      .logInsightSentiment(
        Sentiment.THUMBS_UP,
        ISSUE1.issueDetails.fatality.toCrashType(),
        DEFAULT_AI_INSIGHT,
      )
  }
}
