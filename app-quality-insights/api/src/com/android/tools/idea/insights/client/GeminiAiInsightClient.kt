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
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolverImpl
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.VisibleForTesting

/** Guidelines for the model to provide context and fine tune the response. */
@VisibleForTesting
private val GEMINI_PREAMBLE =
  """
    Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
    For headings, use H3 only. Initial explanation should not be under a heading.
    Begin with the explanation directly. Do not add fillers at the start of response.
  """
    .trimIndent()

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

private val GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_MULTI_FILES_PROMPT =
  """
    Explain this exception from my app running on %s with Android version %s.
    Please reference the provided source code if they are helpful.
    If you think you can guess which files the fix for this crash should be performed in,
    please include at the end of the response the extract phrase \"$FILE_PHRASE\${'$'}files,
    where files is a comma separated list of the fully qualified path of the source files
    in which you think the fix should likely be performed.
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

private val GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_PROMPT =
  """
    Explain this exception from my app running on %s with Android version %s.
    Please reference the provided source code if they are helpful.
    If you think you can guess which single file the fix for this crash should be performed in,
    please include at the end of the response the extract phrase \"$FILE_PHRASE\${'$'}file,
    where file is the fully qualified path of the source file in which you think the fix should likely be performed.
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

private val CONTEXT_PREAMBLE =
  """
    Respond with a comma separated list of the paths of the files, in descending order of relevance.
    If the file is a Java or Kotlin file, convert its package name to path.
  """
    .trimIndent()

private val CONTEXT_PROMPT =
  """
    What are the files relevant for fixing this exception?
    ```
    %s
    ```
  """
    .trimIndent()

const val FILE_PHRASE = "The fix should likely be in "

// Extra space reserved for system preamble
private const val CONTEXT_WINDOW_PADDING = 150

class GeminiAiInsightClient(
  private val project: Project,
  private val cache: AppInsightsCache,
  private val codeContextResolver: CodeContextResolver = CodeContextResolverImpl(project),
) : AiInsightClient {
  private val logger =
    Logger.getInstance("com.android.tools.idea.insights.client.GeminiAiInsightClient")

  override suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight =
    if (StudioFlags.GEMINI_FETCH_REAL_INSIGHT.get()) {
      getCachedInsight(request)?.let {
        return it
      }
      val contextData =
        if (!request.connection.isMatchingProject()) {
          CodeContextData.empty(project)
        } else if (StudioFlags.GEMINI_ASSISTED_CONTEXT_FETCH.get()) {
          queryForRelevantContext(request)
        } else {
          codeContextResolver.getSource(request.connection, request.event.stacktraceGroup)
        }

      val userPrompt = createPrompt(request, contextData.codeContext)
      val finalPrompt =
        buildLlmPrompt(project) {
          systemMessage { text(GEMINI_PREAMBLE, emptyList()) }
          userMessage { text(userPrompt, emptyList()) }
        }
      logger.debug("This is the final prompt:\n$userPrompt")

      val response =
        GeminiPluginApi.getInstance().generate(project, finalPrompt).toList().joinToString("\n")

      AiInsight(response, insightSource = InsightSource.STUDIO_BOT, codeContextData = contextData)
        .also { cache.putAiInsight(request.connection, request.issueId, request.variantId, it) }
    } else {
      // Simulate a delay that would come generating an actual insight
      delay(2000)
      AiInsight(createPrompt(request, emptyList()), insightSource = InsightSource.STUDIO_BOT)
    }

  // Always prefer the insight generated with context regardless of current context sharing setting.
  private fun getCachedInsight(request: GeminiCrashInsightRequest): AiInsight? =
    cache.getAiInsight(
      request.connection,
      request.issueId,
      request.variantId,
      ContextSharingState.ALLOWED,
    )
      ?: if (ContextSharingState.getContextSharingState(project) == ContextSharingState.DISABLED) {
        cache.getAiInsight(
          request.connection,
          request.issueId,
          request.variantId,
          ContextSharingState.DISABLED,
        )
      } else {
        null
      }

  private suspend fun queryForRelevantContext(request: GeminiCrashInsightRequest): CodeContextData {
    if (
      !GeminiPluginApi.getInstance().isAvailable() ||
        !GeminiPluginApi.getInstance().isContextAllowed(project)
    )
      return CodeContextData(emptyList())
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text(CONTEXT_PREAMBLE, emptyList()) }
        userMessage {
          text(String.format(CONTEXT_PROMPT, request.event.prettyStackTrace()), emptyList())
        }
      }

    val response =
      GeminiPluginApi.getInstance().generate(project, prompt).toList().joinToString("\n")

    val fileNames = response.split(",").map { it.trim() }
    logger.debug("Gemini wants to see $fileNames")

    val contextData = codeContextResolver.getSource(fileNames)
    logger.debug(
      "AQI was able to find these context files: ${contextData.codeContext.joinToString { it.filePath }}"
    )

    return contextData
  }

  private fun getPromptWithContext() =
    if (StudioFlags.SUGGEST_A_FIX.get()) {
      if (StudioFlags.STUDIOBOT_TRANSFORM_SESSION_DIFF_EDITOR_VIEWER_ENABLED.get()) {
        GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_MULTI_FILES_PROMPT
      } else {
        GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_PROMPT
      }
    } else {
      GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT
    }

  private fun createPrompt(request: GeminiCrashInsightRequest, context: List<CodeContext>): String {
    val promptWithContext = getPromptWithContext()
    val initialPrompt =
      String.format(
          if (context.isEmpty()) GEMINI_INSIGHT_PROMPT_FORMAT else promptWithContext,
          request.deviceName,
          request.apiLevel,
          request.event.prettyStackTrace(),
        )
        .trim()
    var availableContextSpace =
      GeminiPluginApi.getInstance().MAX_QUERY_CHARS - CONTEXT_WINDOW_PADDING - initialPrompt.count()
    val prompt =
      context
        .takeWhile { ctx ->
          val nextContextString = "\n${ctx.filePath}:\n```\n${ctx.content}\n```"
          availableContextSpace -= nextContextString.count()
          availableContextSpace >= 0
        }
        .fold(initialPrompt) { acc, (path, content) -> "$acc\n${path}:\n```\n$content\n```" }
    return prompt
  }
}

fun createGeminiInsightRequest(
  connection: Connection,
  issueId: IssueId,
  variantId: String?,
  event: Event,
) =
  GeminiCrashInsightRequest(
    connection = connection,
    issueId = issueId,
    variantId = variantId,
    deviceName = event.eventData.device.let { "${it.manufacturer} ${it.model}" },
    apiLevel = event.eventData.operatingSystemInfo.displayVersion,
    event = event,
  )

private fun Event.prettyStackTrace() =
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
