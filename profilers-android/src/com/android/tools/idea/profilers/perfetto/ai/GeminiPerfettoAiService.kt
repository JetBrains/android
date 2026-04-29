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

package com.android.tools.idea.profilers.perfetto.ai

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.buildLlmPrompt
// The sherlock.common module is not part of the monorepo and Google publishes no artifact for it,
// so com.android.tools.sherlock.common.perfetto.ai.PerfettoAiService cannot be imported here.
// This is permanent, not a pending merge step.
// import com.android.tools.sherlock.common.perfetto.ai.PerfettoAiService
import com.intellij.openapi.project.Project

/**
 * Gemini-backed implementation of [PerfettoAiService]. This service uses the [GeminiPluginApi] to send chat queries to the Gemini assistant
 * in Android Studio.
 */
// PerfettoAiService lives in the sherlock.common module, which the monorepo does not carry, so
// this class cannot implement it here and neither generateQuery nor analyzeTrace can be an
// override. This is permanent, not a pending merge step.
// class GeminiPerfettoAiService(private val project: Project) : PerfettoAiService {
class GeminiPerfettoAiService(private val project: Project) {
  fun generateQuery(prompt: String, traceFilePath: String) {
    sendPromptWithSkill(
      "Generate Perfetto SQL Query: $prompt. The trace file is available at: $traceFilePath",
      GeminiPerfettoAiConstants.PERFETTO_SQL_SYSTEM_INSTRUCTION,
    )
  }

  fun analyzeTrace(prompt: String, traceFilePath: String) {
    sendPromptWithSkill(
      "Analyze Perfetto Trace: $prompt. The trace file is available at: $traceFilePath",
      GeminiPerfettoAiConstants.PERFETTO_TRACE_ANALYSIS_SYSTEM_INSTRUCTION,
    )
  }

  /**
   * Helper method to construct an LLM prompt and send it to the Gemini chat window.
   *
   * @param prompt The prompt text to display in the chat and send to the model.
   * @param systemMessageText The system message to guide the AI (e.g., specifying the skill to use).
   */
  private fun sendPromptWithSkill(prompt: String, systemMessageText: String) {
    val api = GeminiPluginApi.getInstance()
    if (!api.isAvailable()) return

    val llmPrompt =
      buildLlmPrompt(project) {
        systemMessage { text(systemMessageText, filesUsed = emptyList()) }
        userMessage { text(prompt, filesUsed = emptyList()) }
      }

    api.sendChatQuery(project, llmPrompt, displayText = prompt, requestSource = GeminiPluginApi.RequestSource.OTHER)
  }
}
