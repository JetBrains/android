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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.IssueId
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay

data class GeminiCrashInsightRequest(
  val connection: Connection,
  val issueId: IssueId,
  val variantId: String?,
  val deviceName: String,
  val apiLevel: String,
  val event: Event,
)

interface AiInsightClient {

  /**
   * Gets AI generated insight for this issue
   *
   * @param request - Additional context required by the insight client to get insights for the crash
   */
  suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight

  companion object {
    fun getClient(project: Project, codeContextResolver: CodeContextResolver) =
      // Returns a stub client for E2E test environment.
      if (java.lang.Boolean.getBoolean("appinsights.generate.fake.insight")) {
        StubAiInsightClient()
      } else {
        GeminiAiInsightClient(project, codeContextResolver)
      }
  }
}

/** Stub client that does not call Gemini. It returns the prompt that would have been sent to Gemini. */
class StubAiInsightClient : AiInsightClient {

  override suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight {
    // Simulate a delay that would come generating an actual insight
    delay(2000)
    return AiInsight(createPrompt(request, emptyList()), request.event, insightSource = InsightSource.STUDIO_BOT)
  }
}
