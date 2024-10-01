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

import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.protobuf.Message
import com.android.tools.idea.studiobot.Content
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.google.android.studio.gemini.CodeSnippet
import com.google.android.studio.gemini.GeminiInsightsRequest
import com.intellij.openapi.project.Project
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

class GeminiAiInsightClient private constructor(private val project: Project) : AiInsightClient {

  override suspend fun fetchCrashInsight(
    projectId: String,
    additionalContextMsg: Message,
  ): AiInsight {
    val request = GeminiInsightsRequest.parser().parseFrom(additionalContextMsg.toByteArray())
    val prompt =
      buildPrompt(project) {
        systemMessage { text(GEMINI_PREAMBLE, emptyList()) }
        userMessage { text(createPrompt(request), emptyList()) }
      }
    val generateContentFlow = StudioBot.getInstance().model(project).generateContent(prompt)
    val response =
      buildString {
          generateContentFlow.collect { content ->
            when (content) {
              // Can't append text from FunctionCall
              is Content.FunctionCall -> {}
              is Content.TextContent -> appendLine(content.text)
            }
          }
        }
        .trim()
    return AiInsight(response, insightSource = InsightSource.STUDIO_BOT)
  }

  private fun createPrompt(request: GeminiInsightsRequest) =
    "${
      String.format(
        if (request.codeSnippetsList.isEmpty()) GEMINI_INSIGHT_PROMPT_FORMAT else GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT,
        request.deviceName,
        request.apiLevel,
        request.stackTrace,
      )
        .trim()
    }${request.codeSnippetsList.toPromptString()}"

  private fun List<CodeSnippet>.toPromptString() =
    if (isEmpty()) ""
    else "\n" + joinToString("\n") { "${it.filePath}:\n```\n${it.codeSnippet}\n```" }

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
