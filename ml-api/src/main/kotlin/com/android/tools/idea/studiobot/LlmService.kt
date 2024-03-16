/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.studiobot

import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.lang.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.kotlin.idea.KotlinLanguage

interface LlmService {
  /**
   * Sends a query to the model and returns the raw response.
   * This must only be called if [StudioBot.isContextAllowed] is true.
   *
   * There is a limit on how many characters the query can contain. This limit may change
   * over time as the model is updated, but as of 12/19/2023 it is approximately 22k.
   * If the query size exceeds the limit, the last 22k chars will be sent.
   * Each string returned is a chunk of the model's response, which can be one of the following:
   *  - A text chunk, represented by its plain text.
   *  - A code chunk, represented using Markdown code block format, with language specified if possible.
   *  - A chunk representing a code chunk which has been blocked for some reason, which will contain "Code snippet blocked.".
   *  - An error chunk representing an API error which will start with "ERROR: " followed by error details.
   *  - A citations chunk containing citations attached with the response, which will start with "CITATIONS: " followed
   *    by a comma-separated list of URLs.
   *
   * @throws IllegalStateException if context sharing is not enabled, or if the prompt does not end with a user message
   */
  suspend fun sendQuery(prompt: Prompt, source: StudioBot.RequestSource): Flow<String>

  /**
   * This is an experimental API that attempts to generate code for a given prompt. It uses AIDA's
   * generateCode endpoint.
   *
   * Currently, this API must only be called if [StudioBot.isContextAllowed] is true.
   *
   * @param prompt The prompt to generate code for.
   * @param nSamples The number of samples to generate.
   * @param language The language in which the code should be generated, one of [JavaLanguage.INSTANCE] or [KotlinLanguage.INSTANCE]
   * @return a list of generated code samples. The list may contain up to [nSamples] elements.
   */
  @Experimental
  suspend fun generateCode(prompt: Prompt, nSamples: Int = 4, language: Language = KotlinLanguage.INSTANCE): List<String>

  open class StubLlmService : LlmService {
    override suspend fun sendQuery(prompt: Prompt, source: StudioBot.RequestSource): Flow<String> = emptyFlow()
    override suspend fun generateCode(prompt: Prompt, nSamples: Int, language: Language) = listOf<String>()
  }
}