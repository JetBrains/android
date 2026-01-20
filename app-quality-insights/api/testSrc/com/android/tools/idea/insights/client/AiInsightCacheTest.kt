/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.TestConnection
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiInsightCacheTest {

  @Test
  fun `get and put AI insights`() {
    val connection = TestConnection("blah", "1234", "project12", "12")
    val cache = AiInsightCache()
    val context =
      CodeContextData(
        listOf(CodeContext("/path", "abc")),
        contextSharingState = ContextSharingState.ALLOWED,
      )

    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, ContextSharingState.DISABLED))
      .isNull()

    cache.putAiInsight(connection, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, ContextSharingState.DISABLED))
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))

    assertThat(cache.getAiInsight(connection, ISSUE1.id, "variant1", ContextSharingState.DISABLED))
      .isNull()
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, ContextSharingState.ALLOWED))
      .isNull()

    val newInsight = AiInsight("blah", ISSUE1.sampleEvent, codeContextData = context)
    cache.putAiInsight(connection, ISSUE1.id, null, newInsight)
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, ContextSharingState.ALLOWED))
      .isEqualTo(newInsight.copy(isCached = true))

    cache.putAiInsight(connection, ISSUE1.id, "variant1", DEFAULT_AI_INSIGHT)
    assertThat(cache.getAiInsight(connection, ISSUE1.id, "variant1", ContextSharingState.DISABLED))
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))
  }
}
