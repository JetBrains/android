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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
   */
  suspend fun sendQuery(request: AiExcludeService.ValidatedQuery, source: StudioBot.RequestSource): Flow<String>

  open class StubLlmService : LlmService {
    override suspend fun sendQuery(request: AiExcludeService.ValidatedQuery, source: StudioBot.RequestSource): Flow<String> = emptyFlow()
  }
}