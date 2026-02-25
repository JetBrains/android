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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.buildLlmPrompt
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.IssueId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.toList

class GeminiAiInsightClient(private val project: Project, private val codeContextResolver: CodeContextResolver) : AiInsightClient {
  private val logger = Logger.getInstance("com.android.tools.idea.insights.client.GeminiAiInsightClient")

  override suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight {
    val contextData =
      if (
        !request.connection.isMatchingProject() ||
          !GeminiPluginApi.getInstance().isAvailable() ||
          !GeminiPluginApi.getInstance().isContextAllowed(project)
      ) {
        CodeContextData.empty(project)
      } else if (StudioFlags.GEMINI_ASSISTED_CONTEXT_FETCH.get()) {
        queryForRelevantContext(request)
      } else {
        codeContextResolver.getSource(request.connection, request.event.stacktraceGroup)
      }

    val userPrompt = createPrompt(request, contextData.codeContext)
    val finalPrompt =
      buildLlmPrompt(project) {
        // Enterprise GCA drops systemMessages, so this has to be a userMessage.
        userMessage { text(if (StudioFlags.AQI_FIX_WITH_AGENT.get()) SHORT_GEMINI_PREAMBLE else GEMINI_PREAMBLE, emptyList()) }
        userMessage { text(userPrompt, emptyList()) }
      }
    logger.debug("This is the final prompt:\n$userPrompt")

    val response = GeminiPluginApi.getInstance().generate(project, finalPrompt).toList().joinToString("\n")

    return AiInsight(response, request.event, insightSource = InsightSource.STUDIO_BOT, codeContextData = contextData)
  }

  private suspend fun queryForRelevantContext(request: GeminiCrashInsightRequest): CodeContextData {
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text(CONTEXT_PREAMBLE, emptyList()) }
        userMessage { text(String.format(CONTEXT_PROMPT, request.event.prettyStackTrace()), emptyList()) }
      }

    val response = GeminiPluginApi.getInstance().generate(project, prompt).toList().joinToString("")

    val fileNames = response.cleanNewLineAndSpace().split(",").map { it.trim() }
    logger.debug("Gemini wants to see $fileNames")

    val contextData = codeContextResolver.getSource(fileNames)
    logger.debug("AQI was able to find these context files: ${contextData.codeContext.joinToString { it.filePath }}")

    return contextData
  }

  private fun String.cleanNewLineAndSpace() = replace("\n", "").replace(" ", "")
}

fun createGeminiInsightRequest(connection: Connection, issueId: IssueId, variantId: String?, event: Event) =
  GeminiCrashInsightRequest(
    connection = connection,
    issueId = issueId,
    variantId = variantId,
    deviceName = event.eventData.device.let { "${it.manufacturer} ${it.model}" },
    apiLevel = event.eventData.operatingSystemInfo.displayVersion,
    event = event,
  )

fun Event.prettyStackTrace() =
  buildString {
      stacktraceGroup.exceptions.forEachIndexed { idx, exception ->
        if (idx == 0 || exception.rawExceptionMessage.shouldTakeException()) {
          appendLine(exception.rawExceptionMessage)
          append(exception.stacktrace.frames.joinToString(separator = "") { "\t${it.rawSymbol}\n" })
        }
      }
    }
    .trim()

private fun String.shouldTakeException() = startsWith("Caused by")
