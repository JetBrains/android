/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.logging

import com.google.protobuf.Struct
import java.time.Duration

/**
 * Data class for AI events.
 */
data class AiEvent(
  val conversation: Conversation,
  val modelId: String,
  val feature: Feature,
) {
  enum class Feature {
    UNKNOWN_FEATURE,
    CHAT,
    AGENT,
    CODE_COMPLETION,
    TRANSFORM_CODE,
  }

  data class Conversation(
    val conversationId: String?,
    val turns: List<Turn>,
  )

  data class Turn(
    val turnIndex: Int?,
    val request: GenerateContentRequest,
    val response: GenerateContentResponse,
    val edits: List<Edit>,
    val toolsInvoked: List<ToolsInvoked>,
    val lastFeedbackProvided: FeedbackProvided?,
  )

  data class GenerateContentRequest(
    val modelId: String,
    val generationConfig: GenerationConfig?,
    val systemInstruction: Content?,
    val tools: List<Tool>,
    val contents: List<Content>,
    val recitationConfig: RecitationConfig?,
  )

  data class Content(
    val role: Role,
    val parts: List<Part>,
  ) {
    enum class Role {
      ROLE_UNSPECIFIED,
      USER,
      MODEL,
    }
  }

  data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
    val isThought: Boolean,
  )

  data class FunctionCall(
    val name: String,
    val arguments: Struct,
  )

  data class FunctionResponse(
    val name: String,
    val response: Struct,
  )

  data class GenerationConfig(
    val temperature: Float,
    val topP: Float,
    val thinkingConfig: ThinkingConfig?,
    val maxOutputTokens: Int,
  )

  data class ThinkingConfig(
    val includeThoughts: Boolean,
    val thinkingBudget: Int,
  )

  data class RecitationConfig(
    val allowCiteRecommendations: Boolean,
    val recitationOverwriteRule: RecitationOverwriteRule,
  )

  enum class RecitationOverwriteRule {
    RECITATION_OVERWRITE_RULE_UNSPECIFIED,
    CODE_AI_POLICY,
  }

  data class Tool(
    val functionDeclarations: List<FunctionDeclaration>,
  )

  data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema,
    val response: Schema,
  )

  data class Schema(
    val type: Type,
    val format: String?,
    val title: String?,
    val description: String?,
    val nullable: Boolean,
    val enumValues: List<String>,
    val items: Schema?,
    val properties: Map<String, Schema>,
    val propertyOrdering: List<String>,
    val required: List<String>,
  ) {
    enum class Type {
      TYPE_UNSPECIFIED,
      STRING,
      NUMBER,
      INTEGER,
      BOOLEAN,
      ARRAY,
      OBJECT,
    }
  }

  data class GenerateContentResponse(
    val candidates: List<Candidate>,
  )

  data class Candidate(
    val content: Content,
    val citationMetadata: CitationMetadata?,
    val finishReason: FinishReason,
  ) {
    enum class FinishReason {
      FINISH_REASON_UNSPECIFIED,
      STOP,
      RECITATION,
    }
  }

  data class CitationMetadata(
    val citations: List<Citation>,
  )

  data class Citation(
    val startIndex: Int,
    val endIndex: Int,
    val uri: String?,
    val title: String?,
    val license: String?,
    val publicationDate: Date?,
  )

  data class Date(
    val year: Int,
    val month: Int,
    val day: Int,
  )

  data class Edit(
    val suggestedEditApplied: Boolean,
    val language: String?,
    val editSuggested: EditDiff,
    val editAccepted: EditDiff?,
    val reviewTime: Duration?,
    val filePath: String,
  )

  data class ToolsInvoked(
    val toolName: String,
    val toolMetadata: Map<String, String>,
  )

  data class EditDiff(
    val charactersAdded: Long,
    val charactersRemoved: Long,
    val linesAdded: Long,
    val linesRemoved: Long,
  )

  enum class FeedbackProvided {
    UNKNOWN_FEEDBACK,
    NO_FEEDBACK,
    POSITIVE_FEEDBACK,
    NEGATIVE_FEEDBACK,
  }
}