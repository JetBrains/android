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
import com.android.tools.idea.studiobot.Content
import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.MalformedPromptException
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.studiobot.prompts.PromptBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
data class PromptImpl(
  override val messages: List<Prompt.Message>,
  override val functions: List<Prompt.Function> = emptyList(),
  override val functionCallingMode: Prompt.FunctionCallingMode = Prompt.FunctionCallingMode.AUTO,
) : Prompt

class PromptBuilderImpl(private val project: Project) : PromptBuilder {
  private val messages = mutableListOf<Prompt.Message>()
  private val functions = mutableListOf<Prompt.Function>()
  private var functionCallingMode = Prompt.FunctionCallingMode.AUTO

  abstract class MessageBuilderImpl : PromptBuilder.MessageBuilder {
    protected val myChunks = mutableListOf<Prompt.Chunk>()

    /** Adds [str] as text in the message. */
    override fun text(str: String, filesUsed: Collection<VirtualFile>) {
      myChunks.add(Prompt.TextChunk(str, filesUsed))
    }

    /** Adds [code] as a formatted code block in the message, with optional [language] specified. */
    override fun code(code: String, language: MimeType?, filesUsed: Collection<VirtualFile>) {
      myChunks.add(Prompt.CodeChunk(code, language, filesUsed))
    }

    override fun blob(data: ByteArray, mimeType: MimeType, filesUsed: Collection<VirtualFile>) {
      myChunks.add(Prompt.BlobChunk(mimeType, filesUsed, data))
    }

    abstract fun build(): Prompt.Message
  }

  inner class SystemMessageBuilderImpl : MessageBuilderImpl() {
    override fun build() = Prompt.SystemMessage(myChunks)
  }

  inner class ModelMessageBuilderImpl : MessageBuilderImpl() {
    override fun build() = Prompt.ModelMessage(myChunks)
  }

  inner class UserMessageBuilderImpl : PromptBuilder.UserMessageBuilder, MessageBuilderImpl() {
    override val project = this@PromptBuilderImpl.project

    override fun build() = Prompt.UserMessage(myChunks)
  }

  inner class ContextMessageBuilderImpl : PromptBuilder.ContextBuilder, MessageBuilderImpl() {
    private val myFiles = mutableListOf<Prompt.ContextFile>()

    override fun virtualFile(file: VirtualFile, isCurrentFile: Boolean, selection: TextRange?) {
      myFiles.add(Prompt.ContextFile(file, isCurrentFile, selection))
    }

    override fun file(file: Prompt.ContextFile) {
      myFiles.add(file)
    }

    override fun build() = Prompt.ContextMessage(myChunks, myFiles)
  }

  inner class FunctionsBuilderImpl : PromptBuilder.FunctionsBuilder {
    override fun function(function: Prompt.Function) {
      functions.add(function)
    }

    override fun functions(functions: List<Prompt.Function>) {
      this@PromptBuilderImpl.functions.addAll(functions)
    }

    override fun setMode(mode: Prompt.FunctionCallingMode) {
      this@PromptBuilderImpl.functionCallingMode = mode
    }
  }

  override fun systemMessage(builderAction: PromptBuilder.MessageBuilder.() -> Unit) {
    if (messages.isNotEmpty()) {
      throw MalformedPromptException(
        "Prompt can only contain one system message, and it must be the first message."
      )
    }
    messages.add(SystemMessageBuilderImpl().apply(builderAction).build())
  }

  override fun userMessage(builderAction: PromptBuilder.UserMessageBuilder.() -> Unit) {
    messages.add(UserMessageBuilderImpl().apply(builderAction).build())
  }

  override fun modelMessage(builderAction: PromptBuilder.MessageBuilder.() -> Unit) {
    messages.add(ModelMessageBuilderImpl().apply(builderAction).build())
  }

  override fun context(builderAction: PromptBuilder.ContextBuilder.() -> Unit) {
    messages.add(ContextMessageBuilderImpl().apply(builderAction).build())
  }

  override fun functions(builderAction: PromptBuilder.FunctionsBuilder.() -> Unit) {
    FunctionsBuilderImpl().apply(builderAction)
  }

  override fun functionCall(call: Content.FunctionCall) {
    messages.add(Prompt.FunctionCallMessage(call))
  }

  override fun functionResponse(name: String, response: String) {
    messages.add(Prompt.FunctionResponseMessage(name, response))
  }

  fun addAll(prompt: Prompt): PromptBuilderImpl {
    messages.addAll(prompt.messages)
    return this
  }

  private fun checkPromptFormat(condition: Boolean, message: String) {
    if (!condition) throw MalformedPromptException(message)
  }

  private fun excludedFiles(): Set<VirtualFile> =
    messages
      .flatMap { msg ->
        msg.chunks.flatMap { chunk -> chunk.filesUsed } +
          ((msg as? Prompt.ContextMessage)?.files?.map { it.virtualFile } ?: emptyList())
      }
      .filter {
        DumbService.getInstance(project).runReadActionInSmartMode<Boolean> {
          // Check aiexclude rules *in smart mode* so that we don't run into
          // ExclusionStatus.INDETERMINATE
          StudioBot.getInstance().aiExcludeService(project).isFileExcluded(it)
        }
      }
      .toSet()

  fun build(): Prompt {
    // Verify that the prompt is well-formed
    checkPromptFormat(messages.isNotEmpty(), "Prompt has no messages.")

    val excludedFiles = excludedFiles()
    if (excludedFiles.isNotEmpty()) {
      throw AiExcludeException(excludedFiles)
    }
    return PromptImpl(messages, functions, functionCallingMode)
  }
}
