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
   * Sends a query to the model and returns the raw response.
   * This must only be called if [StudioBot.isContextAllowed] is true.
   *
   * @throws IllegalStateException if context sharing is not enabled
   * @throws ModelError if the model endpoint throws an exception
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
   * @return a list of generated code samples. The list may contain up to [nSamples] elements.
   * @throws ModelError if the model endpoint throws an exception
   */
  suspend fun generateCode(prompt: Prompt, language: Language, config: GenerationConfig = GenerationConfig(samples = 4)): List<Content>
}

/**
 * The exception thrown by all [Model] methods.
 *
 *  This wrapper class is needed to work around a coroutines issue when
 *  -Dkotlinx.coroutines.debug=on. Flows that are closed or cancelled with an exception of type T
 *  whose cause is set to another exception of type TT end up getting malformed in coroutines
 *  internals, and are emitted as a throwable of type T with cause also of type T, and the cause's cause
 *  is the throwable of type TT.
 *
 *  By implementing [createCopy] as null we avoid this issue, and callers consuming the flow returned by
 *  [Model.generateContent] can catch a [ModelError] and retrieve its [io.grpc.StatusRuntimeException] cause
 *  directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelError(override val cause: io.grpc.StatusRuntimeException, override val message: String? = cause.message): CopyableThrowable<ModelError>, IOException(cause) {
  override fun createCopy(): ModelError? = null
}

/**
 * Configuration options for generation.
 *
 * @param samples The number of samples to generate. Currently, this is only used by the [Model.generateCode] API
 * @param temperature Model temperature. If left unset, the backend will use a default value that may vary from model to model.
 *  Note that not all backends may support customizing the temperature.
 */
data class GenerationConfig(
  val samples: Int = 1,
  val temperature: Float? = null
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