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

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.android.tools.idea.insights.client.AiInsightCache
import com.android.tools.idea.insights.client.AiInsightClient
import com.android.tools.idea.insights.client.createGeminiInsightRequest
import com.android.tools.idea.insights.client.runGrpcCatching
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.IssueId
import com.android.tools.idea.insights.model.stacktrace.StacktraceGroup
import com.intellij.openapi.project.Project

internal const val GEMINI_TOOL_WINDOW_ID = "StudioBot"

/** Exposes AI related tools to AQI. */
abstract class AiInsightToolkit(
  private val project: Project,
  private val codeContextResolver: CodeContextResolver,
  protected val aiInsightClient: AiInsightClient,
  private val insightCache: AiInsightCache = AiInsightCache(),
) {

  abstract val aiInsightOnboardingProvider: InsightsOnboardingProvider

  /**
   * Validates whether an AI insight can be fetched for the [fetchInsight] call.
   *
   * This acts as a pre-fetch check. If this method returns a non-null [LoadingState.Done], the fetch process is short-circuited and the
   * returned state is used as the result.
   *
   * @param failureType the type of failure (e.g., CRASH, ANR).
   * @param event the specific event/occurrence of the issue.
   * @return a [LoadingState.Done] result if the fetch should be intercepted, or null to proceed.
   */
  abstract suspend fun validateFetchInsightPrecondition(failureType: FailureType, event: Event): LoadingState.Done<AiInsight>?

  /**
   * Provides deprecation data related to AI insights only. For deprecation data related to AQI see
   * [com.android.tools.idea.insights.AppInsightsConfigurationManager]
   */
  val insightDeprecationData: DevServicesDeprecationData
    get() {
      val geminiData = getDeprecationData("gemini/gemini", "Gemini")
      return if (geminiData.isUnsupported()) {
        geminiData
      } else {
        getDeprecationData("aqi/insights", "Insights")
      }
    }

  /**
   * Gets the source files for the given [stack].
   *
   * @param conn [Connection] for which the source is needed
   * @param stack [StacktraceGroup] for which the files are needed.
   */
  suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData {
    if (!GeminiPluginApi.getInstance().isContextAllowed(project)) return CodeContextData.DISABLED
    return codeContextResolver.getSource(conn, stack).copy(contextSharingState = ContextSharingState.ALLOWED)
  }

  /** Fetch insight for the given params */
  suspend fun fetchInsight(
    connection: Connection,
    issueId: IssueId,
    variantId: String?,
    failureType: FailureType,
    event: Event,
    forceGenerateNewInsight: Boolean = false,
  ): LoadingState.Done<AiInsight> {
    validateFetchInsightPrecondition(failureType, event)?.let {
      return it
    }
    if (!forceGenerateNewInsight) {
      getCachedInsight(connection, issueId, variantId)?.let {
        return LoadingState.Ready(it)
      }
    }
    val request = createGeminiInsightRequest(connection, issueId, variantId, event)
    val failure = LoadingState.UnknownFailure("Unable to fetch insight for the selected issue.")
    return runGrpcCatching(failure) {
      val insight = aiInsightClient.fetchCrashInsight(request)
      insightCache.putAiInsight(connection, issueId, variantId, insight)
      LoadingState.Ready(insight)
    }
  }

  fun updateInsightFeedback(connection: Connection, issueId: IssueId, variantId: String?, feedback: InsightFeedback) {
    val cachedInsight = getCachedInsight(connection, issueId, variantId) ?: return

    insightCache.putAiInsight(connection, issueId, variantId, cachedInsight.copy(feedback = feedback))
  }

  // Always prefer the insight generated with context regardless of current context sharing setting.
  private fun getCachedInsight(connection: Connection, issueId: IssueId, variantId: String?): AiInsight? =
    insightCache.getAiInsight(connection, issueId, variantId, ContextSharingState.ALLOWED)
      ?: if (ContextSharingState.getContextSharingState(project) == ContextSharingState.DISABLED) {
        insightCache.getAiInsight(connection, issueId, variantId, ContextSharingState.DISABLED)
      } else {
        null
      }

  private fun getDeprecationData(service: String, userFriendlyServiceName: String) =
    DevServicesDeprecationDataProvider.getInstance().getCurrentDeprecationData(service, userFriendlyServiceName)
}
