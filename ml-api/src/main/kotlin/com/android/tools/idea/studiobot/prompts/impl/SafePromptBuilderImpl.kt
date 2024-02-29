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
package com.android.tools.idea.studiobot.prompts.impl

import com.android.tools.idea.studiobot.AiExcludeException
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.MalformedPromptException
import com.android.tools.idea.studiobot.prompts.SafePrompt
import com.android.tools.idea.studiobot.prompts.SafePromptBuilder
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
data class SafePromptImpl(override val messages: List<SafePrompt.Message>) : SafePrompt

class SafePromptBuilderImpl(private val project: Project) : SafePromptBuilder {
  override val messages = mutableListOf<SafePrompt.Message>()

  open class MessageBuilderImpl(
    val makeMessage: (List<SafePrompt.Message.Chunk>) -> SafePrompt.Message
  ) : SafePromptBuilder.MessageBuilder {
    private val myChunks = mutableListOf<SafePrompt.Message.Chunk>()

    /** Adds [str] as text in the message. */
    override fun text(str: String, filesUsed: Collection<VirtualFile>) {
      myChunks.add(SafePrompt.Message.TextChunk(str, filesUsed))
    }

    /** Adds [code] as a formatted code block in the message, with optional [language] specified. */
    override fun code(code: String, language: Language?, filesUsed: Collection<VirtualFile>) {
      myChunks.add(SafePrompt.Message.CodeChunk(code, language, filesUsed))
    }

    fun build() = makeMessage(myChunks)
  }

  inner class UserMessageBuilderImpl(
    makeMessage: (List<SafePrompt.Message.Chunk>) -> SafePrompt.Message
  ) : MessageBuilderImpl(makeMessage), SafePromptBuilder.UserMessageBuilder {
    override val project: Project = this@SafePromptBuilderImpl.project
  }

  override fun systemMessage(builderAction: SafePromptBuilder.MessageBuilder.() -> Unit) {
    if (messages.isNotEmpty()) {
      throw MalformedPromptException(
        "Prompt can only contain one system message, and it must be the first message."
      )
    }
    messages.add(MessageBuilderImpl { SafePrompt.SystemMessage(it) }.apply(builderAction).build())
  }

  override fun userMessage(builderAction: SafePromptBuilder.UserMessageBuilder.() -> Unit) {
    messages.add(UserMessageBuilderImpl { SafePrompt.UserMessage(it) }.apply(builderAction).build())
  }

  override fun modelMessage(builderAction: SafePromptBuilder.MessageBuilder.() -> Unit) {
    messages.add(MessageBuilderImpl { SafePrompt.ModelMessage(it) }.apply(builderAction).build())
  }

  fun addAll(prompt: SafePrompt): SafePromptBuilderImpl {
    messages.addAll(prompt.messages)
    return this
  }

  private fun checkPromptFormat(condition: Boolean, message: String) {
    if (!condition) throw MalformedPromptException(message)
  }

  private fun excludedFiles(): Set<VirtualFile> =
    messages
      .flatMap { msg -> msg.chunks.flatMap { chunk -> chunk.filesUsed } }
      .filter { StudioBot.getInstance().aiExcludeService().isFileExcluded(project, it) }
      .toSet()

  fun build(): SafePrompt {
    // Verify that the prompt is well-formed
    checkPromptFormat(messages.isNotEmpty(), "Prompt has no messages.")

    // Check aiexclude rules
    val excludedFiles = excludedFiles()
    if (excludedFiles.isNotEmpty()) {
      throw AiExcludeException(excludedFiles)
    }
    return SafePromptImpl(messages)
  }
}
