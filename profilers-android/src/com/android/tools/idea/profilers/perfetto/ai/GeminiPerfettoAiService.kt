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

import com.android.tools.idea.gemini.GeminiPluginApiV2
import com.android.tools.idea.gemini.LlmChatInToolWindowResult
// The sherlock.common module is not part of the monorepo and Google publishes no artifact for it,
// so com.android.tools.sherlock.common.perfetto.ai.PerfettoAiService cannot be imported here.
// This is permanent, not a pending merge step.
// import com.android.tools.sherlock.common.perfetto.ai.PerfettoAiService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gemini-backed implementation of [PerfettoAiService]. This service uses [GeminiPluginApiV2] to send chat queries to the Gemini agent in
 * Android Studio.
 */
// PerfettoAiService lives in the sherlock.common module, which the monorepo does not carry, so
// this class cannot implement it here and neither generateQuery nor analyzeTrace can be an
// override. This is permanent, not a pending merge step.
// class GeminiPerfettoAiService(private val project: Project, private val scope: CoroutineScope) : PerfettoAiService {
class GeminiPerfettoAiService(private val project: Project, private val scope: CoroutineScope) {

  companion object {
    private val LOG = Logger.getInstance(GeminiPerfettoAiService::class.java)
  }

  fun generateQuery(prompt: String, traceFilePath: String) {
    sendPromptToAgent("Generate Perfetto SQL Query: $prompt. The trace file is available at: $traceFilePath")
  }

  fun analyzeTrace(prompt: String, traceFilePath: String) {
    sendPromptToAgent("Analyze Perfetto Trace: $prompt. The trace file is available at: $traceFilePath")
  }

  /**
   * Helper method to send a query to the Gemini agent in the tool window.
   *
   * @param prompt The prompt text to send to the model.
   */
  private fun sendPromptToAgent(prompt: String) {
    scope.launch {
      try {
        val api = GeminiPluginApiV2.getInstance()
        when (val result = api.submitQueryInToolWindow(project, prompt)) {
          is LlmChatInToolWindowResult.Success -> {
            LOG.info("Successfully submitted query to Gemini agent.")
          }
          is LlmChatInToolWindowResult.RequestNotSubmitted -> {
            LOG.warn("Failed to submit query to Gemini tool window: ${result.reason}")
          }
        }
      } catch (e: Exception) {
        LOG.warn("Exception while submitting query to Gemini tool window", e)
      }
    }
  }
}
