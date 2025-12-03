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
package com.android.tools.idea.vitals

import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.AiInsightToolkit
import com.android.tools.idea.insights.ai.InsightsOnboardingProvider
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.client.AiInsightClient
import com.android.tools.idea.insights.client.createGeminiInsightRequest
import com.android.tools.idea.insights.client.runGrpcCatchingWithSupervisorScope
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.IssueId
import com.intellij.openapi.project.Project

class VitalsAiInsightToolkit(
  project: Project,
  override val aiInsightOnboardingProvider: InsightsOnboardingProvider,
  codeContextResolver: CodeContextResolver,
  aiInsightClient: AiInsightClient,
) : AiInsightToolkit(project, codeContextResolver, aiInsightClient) {
  override suspend fun fetchInsight(
    connection: Connection,
    issueId: IssueId,
    variantId: String?,
    failureType: FailureType,
    event: Event,
  ): LoadingState.Done<AiInsight> {
    when {
      failureType != FailureType.FATAL ->
        return LoadingState.UnsupportedOperation("Insights are currently not available for ANRs")
      event.isNativeCrash() ->
        return LoadingState.UnsupportedOperation(
          "Insights are currently not available for native crashes"
        )
    }
    val failure = LoadingState.UnknownFailure("Unable to fetch insight for the selected issue.")
    return runGrpcCatchingWithSupervisorScope(failure) {
      LoadingState.Ready(
        aiInsightClient.fetchCrashInsight(
          createGeminiInsightRequest(connection, issueId, variantId, event)
        )
      )
    }
  }
}

private const val ANDROID_NATIVE_CRASH_HEADER =
  "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"
private val PID_REGEX = Regex("^pid: (\\d+), tid: (\\d+) >>> (.+?) <<<$")

private fun Event.isNativeCrash() =
  stacktraceGroup.exceptions.any { it.rawExceptionMessage.isNativeCrashHeader() }

private fun String.isNativeCrashHeader() =
  equals(ANDROID_NATIVE_CRASH_HEADER) || contains(PID_REGEX) || startsWith("backtrace:")
