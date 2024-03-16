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

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile

/**
 * A well-formed prompt that can be understood by the models used by Studio Bot,
 * and has been validated to conform to aiexclude rules in the project.
 * See [buildPrompt] for information on the format and how to construct a prompt.
 */
interface Prompt {
  val messages: List<Message>

  sealed class Message(open val chunks: List<Chunk>) {
    sealed class Chunk(open val text: String, open val filesUsed: Collection<VirtualFile>)

    data class TextChunk(override val text: String, override val filesUsed: Collection<VirtualFile>)
      : Chunk(text, filesUsed)

    data class CodeChunk(override val text: String, val language: Language?, override val filesUsed: Collection<VirtualFile>)
      : Chunk(text, filesUsed)
  }

  data class SystemMessage(override val chunks: List<Chunk>) : Message(chunks)

  data class UserMessage(override val chunks: List<Chunk>) : Message(chunks)

  data class ModelMessage(override val chunks: List<Chunk>) : Message(chunks)
}