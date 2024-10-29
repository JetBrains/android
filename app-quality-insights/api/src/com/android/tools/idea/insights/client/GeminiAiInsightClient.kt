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
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.protobuf.Message
import com.google.android.studio.gemini.CodeSnippet
import com.google.android.studio.gemini.GeminiInsightsRequest
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.VisibleForTesting

/** Guidelines for the model to provide context and fine tune the response. */
@VisibleForTesting
const val GEMINI_PREAMBLE =
  """
    Begin with the explanation directly. Do not add fillers at the start of response.
  """

private val GEMINI_INSIGHT_PROMPT_FORMAT =
  """
    Explain this exception from my app running on %s with Android version %s:
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

private val GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT =
  """
    Explain this exception from my app running on %s with Android version %s.
    Please reference the provided source code if they are helpful.
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

// Extra space reserved for system preamble
private const val CONTEXT_WINDOW_PADDING = 150

class GeminiAiInsightClient private constructor(private val project: Project) : AiInsightClient {

  override suspend fun fetchCrashInsight(
    projectId: String,
    additionalContextMsg: Message,
  ): AiInsight {
    val request = GeminiInsightsRequest.parser().parseFrom(additionalContextMsg.toByteArray())
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text(GEMINI_PREAMBLE, emptyList()) }
        userMessage { text(createPrompt(request), emptyList()) }
      }
    return if (StudioFlags.GEMINI_FETCH_REAL_INSIGHT.get()) {
      val response =
        GeminiPluginApi.getInstance().generate(project, prompt).toList().joinToString("")
      AiInsight(response, insightSource = InsightSource.STUDIO_BOT)
    } else {
      // Simulate a delay that would come generating an actual insight
      delay(2000)
      AiInsight(createPrompt(request), insightSource = InsightSource.STUDIO_BOT)
    }
  }

  private fun createPrompt(request: GeminiInsightsRequest): String {
    val initialPrompt =
      String.format(
          if (request.codeSnippetsList.isEmpty()) GEMINI_INSIGHT_PROMPT_FORMAT
          else GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT,
          request.deviceName,
          request.apiLevel,
          request.stackTrace,
        )
        .trim()
    var availableContextSpace =
      GeminiPluginApi.getInstance().MAX_QUERY_CHARS - CONTEXT_WINDOW_PADDING - initialPrompt.count()
    val codeContextPrompt =
      request.codeSnippetsList
        .takeWhile { codeSnippet ->
          val nextContextString = "\n${codeSnippet.filePath}:\n```\n${codeSnippet.codeSnippet}\n```"
          availableContextSpace -= nextContextString.count()
          availableContextSpace >= 0
        }
        .fold("") { acc, codeSnippet ->
          "$acc\n${codeSnippet.filePath}:\n```\n${codeSnippet.codeSnippet}\n```"
        }
    return "$initialPrompt$codeContextPrompt"
  }

  companion object {
    fun create(project: Project) = GeminiAiInsightClient(project)
  }
}

fun createGeminiInsightRequest(event: Event, codeContextData: CodeContextData) =
  GeminiInsightsRequest.newBuilder()
    .apply {
      val device = event.eventData.device.let { "${it.manufacturer} ${it.model}" }
      val api = event.eventData.operatingSystemInfo.displayVersion
      val eventStackTrace = event.prettyStackTrace()

      deviceName = device
      apiLevel = api
      stackTrace = eventStackTrace

      addAllCodeSnippets(
        codeContextData.codeContext.map { context ->
          CodeSnippet.newBuilder()
            .apply {
              codeSnippet = context.content
              filePath = context.filePath
            }
            .build()
        }
      )
    }
    .build()

private fun Event.prettyStackTrace() =
  buildString {
      stacktraceGroup.exceptions.forEachIndexed { idx, exception ->
        if (idx == 0 || exception.rawExceptionMessage.startsWith("Caused by")) {
          appendLine(exception.rawExceptionMessage)
          append(exception.stacktrace.frames.joinToString(separator = "") { "\t${it.rawSymbol}\n" })
        }
      }
    }
    .trim()
