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
package com.android.tools.idea.gemini

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * [GeminiPluginApi] is a gateway from the android plugin to access functionality provided by the
 * Gemini plugin. There are a few things to note when using this API:
 * 1. The gemini plugin itself may be disabled by some users, and many users may not have logged in
 *    to Gemini. Use [isAvailable] to check if the gemini plugin is in a usable state.
 * 2. The Gemini plugin provides two privacy options for users: a) A project level setting that
 *    controls whether the user has opted in to sharing context. Use [isContextAllowed] if you want
 *    to automatically collect and use context while creating a prompt to send to Gemini. b) A
 *    gitignore style .aiexclude file that allows users to opt some files out the context sharing
 *    mode. Use [isFileExcluded] to check whether a given file is impacted.
 *
 * Functionality-wise, the main APIs are [sendChatQuery] and [stageChatQuery] which provide ways to
 * initiate a conversation in the chat UI, and [generate] which can be used to retrieve the LLM
 * response for a prompt.
 */
interface GeminiPluginApi {
  /**
   * The maximum number of characters a query can contain before it starts getting cut off starting
   * from the end. This is an approximate value derived from the number of tokens supported by the
   * current model.
   */
  val MAX_QUERY_CHARS: Int

  /** Returns whether Gemini is available (user has logged in and onboarded). */
  fun isAvailable(): Boolean = false

  /**
   * Returns whether the user has opted into sharing context from [project] in Studio Bot queries.
   */
  fun isContextAllowed(project: Project): Boolean = false

  /**
   * Returns `true` if one or more `.aiexclude` files in [project] block [file], if [file] is not
   * part of [project], or if the file's exclusion cannot currently be ruled out.
   */
  fun isFileExcluded(project: Project, file: VirtualFile): Boolean = true

  /**
   * Sends a query to the model, using the chat UI.
   *
   * The query will be sent "as-is", without a preamble, chat history, or retrieved facts being
   * added.
   *
   * @param project The active project.
   * @param prompt The validated request containing the text to be sent to the model.
   * @param displayText How the query should appear in the chat timeline. This will default to the
   *   last user query if not specified.
   * @throws IllegalStateException If the prompt does not end with a user message
   */
  fun sendChatQuery(project: Project, prompt: LlmPrompt, displayText: String? = null)

  /**
   * Stages a string in the Studio Bot query bar. The user may choose to submit it, or clear/modify
   * it. You should keep the message short enough to fit in the query bar so it can be inspected by
   * the user before they send it.
   */
  fun stageChatQuery(project: Project, prompt: String)

  /** [generate] returns the (text only) LLM's response to the given [prompt]. */
  suspend fun generate(prompt: LlmPrompt): String = ""

  companion object {
    val EP_NAME =
      ExtensionPointName.create<GeminiPluginApi>("com.android.tools.idea.gemini.geminiPluginApi")

    private val geminiUnavailable =
      object : GeminiPluginApi {
        override val MAX_QUERY_CHARS: Int = Int.MAX_VALUE

        override fun sendChatQuery(project: Project, prompt: LlmPrompt, displayText: String?) {}

        override fun stageChatQuery(project: Project, prompt: String) {}
      }

    fun getInstance(): GeminiPluginApi {
      return EP_NAME.extensionList.firstOrNull() ?: geminiUnavailable
    }
  }

  /** Used for gathering metrics, like how many queries come from each part of Android Studio. */
  enum class RequestSource {
    SYNC,
    BUILD,
    DESIGN_TOOLS,
    EDITOR,
    PLAY_VITALS,
    CRASHLYTICS,
    LOGCAT,
    PROMPT_LIBRARY,
    OTHER,
  }
}
