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

/**
 * Service which lets clients communicate with the StudioBot backend via the chat UI.
 *
 * Use [StudioBot.chat] to look up an implementation of this service.
 *
 * There are two ways to send messages into the chat window:
 *
 * Using [sendChatQuery], queries may be sent directly to the model, and given a truncated
 * representation in the chat timeline.
 *
 * Alternatively, using [stageChatQuery] text can be pasted into the query bar for the user to
 * manually review and send. Use this method if, for example, you want to send a query that includes
 * information from the project, but the context sharing setting is disabled.
 */
interface ChatService {

  /**
   * Sends a query to the model, using the chat UI.
   *
   * The query will be sent "as-is", without a preamble, chat history, or retrieved facts being
   * added.
   *
   * @param prompt The validated request containing the text to be sent to the model.
   * @param requestSource The source of the query in Android Studio
   * @param displayText How the query should appear in the chat timeline. This will default to the
   *   last user query if not specified.
   * @throws IllegalStateException If the prompt does not end with a user message
   */
  fun sendChatQuery(
    prompt: Prompt,
    requestSource: StudioBot.RequestSource,
    displayText: String? = null,
  ) {}

  /**
   * Stages a string in the Studio Bot query bar. The user may choose to submit it, or clear/modify
   * it. You should keep the message short enough to fit in the query bar so it can be inspected by
   * the user before they send it.
   */
  fun stageChatQuery(prompt: String, requestSource: StudioBot.RequestSource) {}

  open class StubChatService : ChatService {
    override fun sendChatQuery(
      prompt: Prompt,
      requestSource: StudioBot.RequestSource,
      displayText: String?,
    ) {}

    override fun stageChatQuery(prompt: String, requestSource: StudioBot.RequestSource) {}
  }
}
