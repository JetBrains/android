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

import com.android.tools.idea.studiobot.prompts.FileWithSelection
import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A generative language model capable of generating content given a prompt. */
interface Model {
  /** Model's configuration. */
  fun config(): ModelConfig

  /**
   * This is an experimental API that attempts to generate code for a given prompt. It uses AIDA's
   * generateCode endpoint.
   *
   * Use the samples parameter of [GenerationConfig] to request a certain number of generations.
   *
   * @param userQuery An optional explicit query from the user to guide the generation process.
   * @param fileContext The file containing selected code; also specifies generation language.
   * @param language The language to generate code in.
   * @param config Configuration options for the backend.
   * @param history Previous prompts and generated code.
   * @return a list of generated code samples. The list may contain up to [nSamples] elements.
   * @throws [StatusRuntimeException] if the model endpoint throws an exception.
   */
  suspend fun generateCode(
    userQuery: String,
    fileContext: FileWithSelection?,
    language: MimeType,
    config: GenerationConfig,
    history: Prompt? = null,
    legacyClientSidePrompt: Prompt? = null,
  ): List<Content>

  /**
   * Sends a query to the model and returns the raw response.
   *
   * @param prompt The prompt to generate code for.
   * @param config Configuration options for the backend.
   * @throws StatusRuntimeException if the model endpoint throws an exception.
   */
  fun generateContent(prompt: Prompt, config: GenerationConfig = GenerationConfig()): Flow<Content>
}

/**
 * Static information about a model.
 *
 * @property supportedBlobTypes mime types supported in input prompts for multi-modal models.
 * @property inputTokenLimit maximum number of tokens allowed in the input prompt for this model.
 * @property outputTokenLimit maximum allowed value for [GenerationConfig.maxOutputTokens] for this
 *   model.
 * @property supportsFunctionCalling Whether this model supports function calling. If it doesn't,
 *   then any prompts passed to it should not contain any function declarations.
 */
data class ModelConfig(
  val supportedBlobTypes: Set<MimeType> = emptySet(),
  val inputTokenLimit: Int,
  val outputTokenLimit: Int,
  val supportsFunctionCalling: Boolean = false,
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

/** The common data type returned by all models. */
sealed interface Content {
  val text: String

  /**
   * @param text The text content of the response.
   * @param citations The list of citations identified for the response content. Inspect each
   *   citation's [CitationAction] to see what the necessary action to take is.
   * @param metadata Arbitrary metadata attached to the response. Mostly to be used internally.
   */
  data class TextContent(
    override val text: String,
    val citations: List<Citation> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
  ) : Content

  /**
   * A function call from a model which supports function calling and was provided with at least one
   * function declaration.
   *
   * @param name The name of the function
   * @param args Arguments provided for that function
   */
  data class FunctionCall(val name: String, val args: Map<String, Any?>) : Content {
    override val text
      get() = throw IllegalStateException("Should not call .text on a FunctionCall")
  }
}

/**
 * A citation identified for a particular response.
 *
 * @param action The necessary action that must be taken in response to this citation.
 * @param url The url source that should be cited if the action is [CitationAction.CITE_INDIRECT] or
 *   [CitationAction.CITE_DIRECT]
 * @param range The range in [Content.TextContent] that is influenced by this citation
 */
data class Citation(
  val action: CitationAction,
  val url: String? = null,
  val range: TextRange = TextRange.EMPTY_RANGE,
)

enum class CitationAction {
  /**
   * A reference that had an indirect (or "minor") influence on the generation. These references
   * must be shown to the user, but the UI can decide where to place them.
   */
  CITE_INDIRECT,

  /**
   * A reference that had a direct (or "heavy") influence on the generation. The UI must take an
   * effort to display them as close the part of the generation that was influenced by this
   * reference.
   */
  CITE_DIRECT,

  /**
   * The content should be blocked. The UI can choose to do nothing with it, or indicate that the
   * response was blocked.
   */
  BLOCK,
}

open class StubModel : Model {
  override fun config() = ModelConfig(inputTokenLimit = 1024, outputTokenLimit = 1024)

  override fun generateContent(prompt: Prompt, config: GenerationConfig) = emptyFlow<Content>()

  override suspend fun generateCode(
    userQuery: String,
    fileContext: FileWithSelection?,
    language: MimeType,
    config: GenerationConfig,
    history: Prompt?,
    legacyClientSidePrompt: Prompt?,
  ): List<Content> = emptyList()
}
