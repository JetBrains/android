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
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.intellij.openapi.project.Project

/** Exposes AI related tools to AQI. */
interface AiInsightToolkit {
  val aiInsightOnboardingProvider: InsightsOnboardingProvider

  /**
   * Provides deprecation data related to AI insights only. For deprecation data related to AQI see
   * [com.android.tools.idea.insights.AppInsightsConfigurationManager]
   */
  val insightDeprecationData: DevServicesDeprecationData

  /**
   * Gets the source files for the given [stack].
   *
   * @param conn [Connection] for which the source is needed
   * @param stack [StacktraceGroup] for which the files are needed.
   */
  suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData
}

internal const val GEMINI_TOOL_WINDOW_ID = "StudioBot"

class AiInsightToolkitImpl(
  private val project: Project,
  override val aiInsightOnboardingProvider: InsightsOnboardingProvider,
  private val codeContextResolver: CodeContextResolver,
) : AiInsightToolkit {

  override val insightDeprecationData = run {
    val geminiData = getDeprecationData("gemini/gemini", "Gemini")
    if (geminiData.isUnsupported()) {
      geminiData
    } else {
      getDeprecationData("aqi/insights", "Insights")
    }
  }

  override suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData {
    if (!GeminiPluginApi.getInstance().isContextAllowed(project)) return CodeContextData.DISABLED
    return codeContextResolver
      .getSource(conn, stack)
      .copy(contextSharingState = ContextSharingState.ALLOWED)
  }

  private fun getDeprecationData(service: String, userFriendlyServiceName: String) =
    DevServicesDeprecationDataProvider.getInstance()
      .getCurrentDeprecationData(service, userFriendlyServiceName)
}
