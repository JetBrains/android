/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import org.jetbrains.annotations.VisibleForTesting

/** Guidelines for the model to provide context and fine tune the response. */
@VisibleForTesting
internal val GEMINI_PREAMBLE =
  """
  Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
  For headings, use H3 only. Initial explanation should not be under a heading.
  Begin with the explanation directly. Do not add fillers at the start of response.
  """
    .trimIndent()

internal val SHORT_GEMINI_PREAMBLE =
  """
  Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
  Do no include headings. Begin with the explanation directly. Do not add fillers at the start of
  response. Respond in no more than four sentences.
  """
    .trimIndent()

internal val GEMINI_INSIGHT_PROMPT_FORMAT =
  """
  Explain this exception from my app running on %s with Android version %s:
  Exception:
  ```
  %s
  ```
  """
    .trimIndent()

internal val GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT =
  """
  Explain this exception from my app running on %s with Android version %s.
  Please reference the provided source code if they are helpful.
  Exception:
  ```
  %s
  ```
  """
    .trimIndent()

internal val GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_MULTI_FILES_PROMPT =
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

internal val GEMINI_INSIGHT_CODE_CONTEXT_WITH_FIX_PROMPT =
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

internal val CONTEXT_PREAMBLE =
  """
  Respond with a comma separated list of the paths of the files, in descending order of relevance.
  If the file is a Java or Kotlin file, convert its package name to path.
  """
    .trimIndent()

internal val CONTEXT_PROMPT =
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

internal fun createPrompt(request: GeminiCrashInsightRequest, context: List<CodeContext>): String {
  val promptWithContext = getPromptWithContext()
  val initialPrompt =
    String.format(
        if (context.isEmpty()) GEMINI_INSIGHT_PROMPT_FORMAT else promptWithContext,
        request.deviceName,
        request.apiLevel,
        request.event.prettyStackTrace(),
      )
      .trim()
  var availableContextSpace = GeminiPluginApi.getInstance().MAX_QUERY_CHARS - CONTEXT_WINDOW_PADDING - initialPrompt.count()
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
