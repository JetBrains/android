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
package com.android.tools.idea.studiobot.prompts

import com.android.tools.idea.studiobot.Content
import com.android.tools.idea.studiobot.MimeType
import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * A well-formed prompt that can be understood by the models used by Studio Bot, and has been
 * validated to conform to .aiexclude rules and context sharing setting in the project. See
 * [buildPrompt] for information on the format and how to construct a prompt.
 */
interface Prompt {
  val messages: List<Message>

  val functions: List<Function>

  val functionCallingMode: FunctionCallingMode

  sealed class Message(open val chunks: List<Chunk>) {
    sealed class Chunk(open val filesUsed: Collection<VirtualFile>)

    data class TextChunk(val text: String, override val filesUsed: Collection<VirtualFile>) :
      Chunk(filesUsed)

    data class CodeChunk(
      val text: String,
      val language: Language?,
      override val filesUsed: Collection<VirtualFile>,
    ) : Chunk(filesUsed)

    data class BlobChunk(
      val mimeType: MimeType,
      override val filesUsed: Collection<VirtualFile>,
      val data: ByteArray,
    ) : Chunk(filesUsed) {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlobChunk

        if (mimeType != other.mimeType) return false
        if (filesUsed != other.filesUsed) return false
        if (!data.contentEquals(other.data)) return false

        return true
      }

      override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + filesUsed.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
      }
    }
  }

  // -- Regular messages --

  data class SystemMessage(override val chunks: List<Chunk>) : Message(chunks)

  data class UserMessage(override val chunks: List<Chunk>) : Message(chunks)

  data class ModelMessage(override val chunks: List<Chunk>) : Message(chunks)

  data class FunctionCallMessage(val call: Content.FunctionCall) : Message(emptyList())

  data class FunctionResponseMessage(val name: String, val response: String) : Message(emptyList())

  // -- Context --
  data class ContextFile(
    val virtualFile: VirtualFile,
    val isCurrentFile: Boolean = false,
    val selection: TextRange? = null,
  )

  data class Context(val files: List<ContextFile>) : Message(emptyList())

  sealed class FunctionParameterType(val name: kotlin.String) {
    object String : FunctionParameterType("string")

    object Integer : FunctionParameterType("integer")

    object Number : FunctionParameterType("number")

    object Boolean : FunctionParameterType("boolean")

    data class Enum(val enumType: FunctionParameterType, val values: List<kotlin.String>) :
      FunctionParameterType("enum")
  }

  data class FunctionParameter(
    val name: String,
    val type: FunctionParameterType,
    val description: String,
    val required: Boolean,
  )

  /**
   * You can use the function calling mode to define the execution behavior for function calling.
   */
  enum class FunctionCallingMode {
    /**
     * The default model behavior. The model decides to predict either a function call or a natural
     * language response.
     */
    AUTO,

    /** The model is constrained to always predict a function call. */
    ANY,

    /**
     * The model won't predict a function call. In this case, the model behavior is the same as if
     * you don't pass any function declarations.
     */
    NONE,
  }

  data class Function(
    val name: String,
    val description: String,
    val parameters: List<FunctionParameter>,
  )
}
