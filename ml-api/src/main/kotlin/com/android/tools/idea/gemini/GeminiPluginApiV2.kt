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
package com.android.tools.idea.gemini

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** Defines a Blob containing data and a mimeType string (e.g., image/png). */
data class LlmBlob(val data: ByteArray, val mimeType: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LlmBlob

    if (!data.contentEquals(other.data)) return false
    if (mimeType != other.mimeType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + mimeType.hashCode()
    return result
  }
}

/** Optional metadata or structured data payloads to include in user queries. */
sealed interface LlmUserQueryPayload {
  data class Reference(val promptString: String, val textRangeStart: Int, val textRangeEnd: Int) : LlmUserQueryPayload
}

/** Defines the target editor tab for an interaction. */
sealed interface LlmEditorTarget {
  data object NewTab : LlmEditorTarget

  data class ExistingTab(val editorTabId: String) : LlmEditorTarget
}

/** Defines the target conversation instance for an interaction. */
sealed interface LlmConversationTarget {
  data object NewConversation : LlmConversationTarget

  data class Existing(val conversationId: String) : LlmConversationTarget
}

/** The reason why a chat request was not submitted. */
enum class LlmFailureReason {
  NO_MODELS_AVAILABLE
}

/** The outcome of trying to submit a query into an editor tab. */
sealed interface LlmChatInEditorResult {
  data class Success(val conversationId: String, val editorTabId: String) : LlmChatInEditorResult

  data class RequestNotSubmitted(val reason: LlmFailureReason) : LlmChatInEditorResult
}

/** The outcome of trying to submit a query into the tool window. */
sealed interface LlmChatInToolWindowResult {
  data class Success(val conversationId: String) : LlmChatInToolWindowResult

  data class RequestNotSubmitted(val reason: LlmFailureReason) : LlmChatInToolWindowResult
}

/** A gateway to the V2 chat and conversation API, wrapping `ChatInteractionService`. */
interface GeminiPluginApiV2 {
  /** Returns whether Gemini V2 APIs are available (if any model provider is ready and has available models.). */
  fun isAvailable(): Boolean = false

  suspend fun submitQueryInEditor(
    project: Project,
    query: String,
    blobs: List<LlmBlob> = emptyList(),
    payloads: List<LlmUserQueryPayload> = emptyList(),
    editorTarget: LlmEditorTarget = LlmEditorTarget.NewTab,
    conversationTarget: LlmConversationTarget = LlmConversationTarget.NewConversation,
  ): LlmChatInEditorResult

  suspend fun submitQueryInToolWindow(
    project: Project,
    query: String,
    blobs: List<LlmBlob> = emptyList(),
    payloads: List<LlmUserQueryPayload> = emptyList(),
    conversationTarget: LlmConversationTarget = LlmConversationTarget.NewConversation,
  ): LlmChatInToolWindowResult

  companion object {
    val EP_NAME = ExtensionPointName.create<GeminiPluginApiV2>("com.android.tools.idea.gemini.geminiPluginApiV2")

    private val geminiUnavailable =
      object : GeminiPluginApiV2 {
        override fun isAvailable(): Boolean = false

        override suspend fun submitQueryInEditor(
          project: Project,
          query: String,
          blobs: List<LlmBlob>,
          payloads: List<LlmUserQueryPayload>,
          editorTarget: LlmEditorTarget,
          conversationTarget: LlmConversationTarget,
        ): LlmChatInEditorResult {
          return LlmChatInEditorResult.RequestNotSubmitted(LlmFailureReason.NO_MODELS_AVAILABLE)
        }

        override suspend fun submitQueryInToolWindow(
          project: Project,
          query: String,
          blobs: List<LlmBlob>,
          payloads: List<LlmUserQueryPayload>,
          conversationTarget: LlmConversationTarget,
        ): LlmChatInToolWindowResult {
          return LlmChatInToolWindowResult.RequestNotSubmitted(LlmFailureReason.NO_MODELS_AVAILABLE)
        }
      }

    fun getInstance(): GeminiPluginApiV2 {
      return EP_NAME.extensionList.firstOrNull() ?: geminiUnavailable
    }
  }
}
