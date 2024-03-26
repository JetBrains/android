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
package com.android.tools.idea.studiobot

import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.lang.Language
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.IOException

/**
 * A generative language model capable of generating content given a prompt.
 */
interface Model {
  /**
   * For multimodal models, returns the list of supported data types that can be contained
   * in passed [Prompt]s. For text-only models, returns an empty list.
   */
  fun supportedBlobs(): List<MimeType> = emptyList()

  /**
   * Sends a query to the model and returns the raw response.
   * This must only be called if [StudioBot.isContextAllowed] is true.
   *
   * @param prompt The prompt to generate code for.
   * @param config Configuration options for the backend.
   * @throws IllegalStateException if context sharing is not enabled
   * @throws IOException if the model endpoint throws an exception. To identify the cause of the error, use [ExceptionUtil.getRootCause].
   *  The result should be an [io.grpc.StatusRuntimeException]. Because of issues with coroutines debugging (see [CopyableThrowable]),
   *  the cause exception can end up nested a layer deeper than expected, but using getRootCause avoids this problem.
   */
  fun generateContent(prompt: Prompt, config: GenerationConfig = GenerationConfig()): Flow<Content>

  /**
   * This is an experimental API that attempts to generate code for a given prompt. It uses AIDA's
   * generateCode endpoint.
   *
   * Currently, this API must only be called if [StudioBot.isContextAllowed] is true.
   * Use the samples parameter of [GenerationConfig] to request a certain number of generations.
   *
   * @param prompt The prompt to generate code for.
   * @param language The language to generate code in.
   * @param config Configuration options for the backend.
   * @return a list of generated code samples. The list may contain up to [nSamples] elements.
   * @throws IOException if the model endpoint throws an exception. To identify the cause of the error, use [ExceptionUtil.getRootCause].
   *  The result should be an [io.grpc.StatusRuntimeException]. Because of issues with coroutines debugging (see [CopyableThrowable]),
   *  the cause exception can end up nested a layer deeper than expected, but using getRootCause avoids this problem.
   */
  suspend fun generateCode(prompt: Prompt, language: Language, config: GenerationConfig = GenerationConfig(candidateCount = 4)): List<Content>
}

/**
 * Configuration options for generation.
 * Note that not all parameters may be configurable for every model.
 *
 * @param candidateCount The number of samples to generate. This isn't supported by streaming APIs like [Model.generateContent], but is for some
 *  implementations of [Model.generateCode].
 * @param temperature Model temperature. If left unset, the backend will use a default value that may vary from model to model.
 * @param maxOutputTokens The maximum number of tokens to include in a candidate.
 * @param stopSequences The set of character sequences that will stop output generation.
 *  If specified, the API will stop at the first appearance of a stop sequence. The stop sequence will not be included as part of the response.
 */
data class GenerationConfig(
  val candidateCount: Int = 1,
  val temperature: Float? = null,
  val maxOutputTokens: Int? = null,
  val stopSequences: List<String> = emptyList()
)

/**
 * The common data type returned by all models.
 */
class Content(
  val text: String
)

open class StubModel: Model {
  override fun generateContent(prompt: Prompt, config: GenerationConfig) = emptyFlow<Content>()

  override suspend fun generateCode(prompt: Prompt, language: Language, config: GenerationConfig) = emptyList<Content>()
}