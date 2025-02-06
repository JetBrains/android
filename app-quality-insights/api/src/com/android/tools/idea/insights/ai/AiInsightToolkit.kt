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
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.experiments.Experiment
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
   * @param stack [StacktraceGroup] for which the files are needed.
   * @param overrideSourceLimit override source limits for [Experiment.CONTROL]
   */
  suspend fun getSource(
    stack: StacktraceGroup,
    overrideSourceLimit: Boolean = false,
  ): CodeContextData
}

internal const val GEMINI_TOOL_WINDOW_ID = "StudioBot"

class AiInsightToolkitImpl(
  private val project: Project,
  override val aiInsightOnboardingProvider: InsightsOnboardingProvider,
  private val codeContextResolver: CodeContextResolver,
) : AiInsightToolkit {

  override val insightDeprecationData = run {
    val geminiData = getDeprecationData("gemini/gemini")
    if (geminiData.isDeprecated()) {
      geminiData
    } else {
      getDeprecationData("aqi/insights")
    }
  }

  override suspend fun getSource(
    stack: StacktraceGroup,
    overrideSourceLimit: Boolean,
  ): CodeContextData {
    if (!GeminiPluginApi.getInstance().isContextAllowed(project)) return CodeContextData.UNASSIGNED
    return codeContextResolver.getSource(stack, overrideSourceLimit)
  }

  private fun getDeprecationData(service: String) =
    DevServicesDeprecationDataProvider.getInstance().getCurrentDeprecationData(service)
}
