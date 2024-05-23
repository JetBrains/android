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
import java.io.IOException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A generative language model capable of generating content given a prompt. */
interface Model {
  /** Model's configuration. */
  fun config(): ModelConfig

  /**
   * Sends a query to the model and returns the raw response.
   *
   * @param prompt The prompt to generate code for.
   * @param config Configuration options for the backend.
   * @throws IOException if the model endpoint throws an exception. To identify the cause of the
   *   error, use [ExceptionUtil.getRootCause]. The result should be an
   *   [io.grpc.StatusRuntimeException]. Because of issues with coroutines debugging (see
   *   [CopyableThrowable]), the cause exception can end up nested a layer deeper than expected, but
   *   using getRootCause avoids this problem.
   */
  fun generateContent(prompt: Prompt, config: GenerationConfig = GenerationConfig()): Flow<Content>

  /**
   * This is an experimental API that attempts to generate code for a given prompt. It uses AIDA's
   * generateCode endpoint.
   *
   * Use the samples parameter of [GenerationConfig] to request a certain number of generations.
   *
   * @param prompt The prompt to generate code for.
   * @param language The language to generate code in.
   * @param config Configuration options for the backend.
   * @return a list of generated code samples. The list may contain up to [nSamples] elements.
   * @throws IOException if the model endpoint throws an exception. To identify the cause of the
   *   error, use [ExceptionUtil.getRootCause]. The result should be an
   *   [io.grpc.StatusRuntimeException]. Because of issues with coroutines debugging (see
   *   [CopyableThrowable]), the cause exception can end up nested a layer deeper than expected, but
   *   using getRootCause avoids this problem.
   */
  suspend fun generateCode(
    prompt: Prompt,
    language: MimeType,
    config: GenerationConfig = GenerationConfig(candidateCount = 4),
  ): List<Content>
}

/**
 * Static information about a model.
 *
 * @property supportedBlobTypes mime types supported in input prompts for multi-modal models.
 * @property inputTokenLimit maximum number of tokens allowed in the input prompt for this model.
 * @property outputTokenLimit maximum allowed value for [GenerationConfig.maxOutputTokens] for this
 *   model.
 */
data class ModelConfig(
  val supportedBlobTypes: Set<MimeType> = emptySet(),
  val inputTokenLimit: Int,
  val outputTokenLimit: Int,
)

/**
 * Configuration options for generation. Note that not all parameters may be configurable for every
 * model.
 *
 * @param candidateCount The number of samples to generate. This isn't supported by streaming APIs
 *   like [Model.generateContent], but is for some implementations of [Model.generateCode].
 * @param temperature Model temperature. If left unset, the backend will use a default value that
 *   may vary from model to model.
 * @param maxOutputTokens The maximum number of tokens to include in a candidate.
 * @param stopSequences The set of character sequences that will stop output generation. If
 *   specified, the API will stop at the first appearance of a stop sequence. The stop sequence will
 *   not be included as part of the response.
 */
data class GenerationConfig(
  val candidateCount: Int = 1,
  val temperature: Float? = null,
  val maxOutputTokens: Int? = null,
  val stopSequences: List<String> = emptyList(),
)

/**
 * The common data type returned by all models.
 *
 * @param text The text content of the response.
 * @param citations The list of citations identified for the response content. Inspect each
 *   citation's [CitationAction] to see what the necessary action to take is.
 * @param metadata Arbitrary metadata attached to the response. Mostly to be used internally.
 */
data class Content(
  val text: String,
  val citations: List<Citation> = emptyList(),
  val metadata: Map<String, Any> = emptyMap(),
)

/**
 * A citation identified for a particular response.
 *
 * @param action The necessary action that must be taken in response to this citation:
 *     * If it is [CitationAction.BLOCK] the content and url should be blank, and so you can either
 *       choose to do nothing with it, or indicate to the user that a response was received but was
 *       blocked.
 *     * If it is [CitationAction.CITE], the content will still be present, but the citation url
 *       should be shown alongside it when presented to the user.
 *
 * @param url The url source that should be cited if the action is [CitationAction.CITE]
 */
data class Citation(val action: CitationAction, val url: String? = null)

/** See [Citation] */
enum class CitationAction {
  CITE,
  BLOCK,
}

open class StubModel : Model {
  override fun config() = ModelConfig(inputTokenLimit = 1024, outputTokenLimit = 1024)

  override fun generateContent(prompt: Prompt, config: GenerationConfig) = emptyFlow<Content>()

  override suspend fun generateCode(prompt: Prompt, language: MimeType, config: GenerationConfig) =
    emptyList<Content>()
}
