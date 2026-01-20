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

import com.android.tools.idea.insights.CallInProgress
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.client.GeminiAiInsightClient
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.IssueId
import com.intellij.openapi.project.Project

open class FakeAiInsightToolkit(
  project: Project,
  codeContextResolver: CodeContextResolver = FakeCodeContextResolver(emptyList()),
  override val aiInsightOnboardingProvider: InsightsOnboardingProvider =
    StubInsightsOnboardingProvider(),
) :
  AiInsightToolkit(
    project,
    codeContextResolver,
    GeminiAiInsightClient(project, codeContextResolver),
  ) {

  private val fetchInsightCall = CallInProgress<LoadingState.Done<AiInsight>>()

  override suspend fun fetchInsight(
    connection: Connection,
    issueId: IssueId,
    variantId: String?,
    failureType: FailureType,
    event: Event,
  ): LoadingState.Done<AiInsight> = fetchInsightCall.initiateCall()

  suspend fun completeFetchInsightCallWith(value: LoadingState.Done<AiInsight>) =
    fetchInsightCall.completeWith(value)
}
