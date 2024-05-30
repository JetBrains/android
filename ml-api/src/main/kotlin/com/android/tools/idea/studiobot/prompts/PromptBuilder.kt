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

import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.impl.PromptBuilderImpl
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Use this builder to construct prompts for Studio Bot APIs by specifying a series of messages.
 * Each part of the prompt must declare which files from the user's project, if any, it uses.
 *
 * Prompts that used files as context cannot be constructed if the context sharing setting is not
 * enabled: check it with `StudioBot.isContextAllowed(project)`. If it is not enabled, an
 * [IllegalStateException] will be thrown.
 *
 * Additionally, each file must be allowed by aiexclude. Check files using
 * AiExcludeService.isFileExcluded. If a file is not allowed, an AiExcludeException will be thrown.
 *
 * A prompt consists of a series of messages. There are three types of messages currently: system,
 * user, and model.
 *
 * A system message at the start of the prompt can be used to provide a preamble - a set of
 * instructions that the model is supposed to follow in the conversation.
 *
 * A user message is a message sent by the user, while a model message is one that the model
 * responded with.
 *
 * The last user message is what the model is supposed to respond to for this prompt/turn of
 * conversation. But this builder doesn't enforce this as a requirement to allow for partially
 * constructed prompts that are later updated before being sent to the model.
 *
 * Prompts are subject the following constraints; a [MalformedPromptException] will be thrown if any
 * aren't followed:
 * * Prompts must contain at least one message.
 * * There can be at most one system message, and it must be the first message.
 *
 * Use [PromptBuilder.MessageBuilder.text] to add plaintext to a message, and
 * [PromptBuilder.MessageBuilder.code] to add a formatted code block with the given language.
 *
 * Example usage:
 * ```
 * buildPrompt(project) {
 *    systemMessage {
 *      text("You are Studio Bot", filesUsed = emptyList())
 *    }
 *    userMessage {
 *      text("Explain this code")
 *      code("fun f(): Int { return 5 }",
 *        KotlinLanguage.INSTANCE,
 *        filesUsed = listOf(currentFile)
 *      )
 *    }
 *    modelMessage {
 *      text("This code returns 5.", filesUsed = emptyList())
 *    }
 *    userMessage {
 *      text("Can you change it to return 6?", filesUsed = emptyList())
 *    }
 * }
 * ```
 */
inline fun buildPrompt(
  project: Project,
  existingPrompt: Prompt? = null,
  builderAction: PromptBuilder.() -> Unit,
): Prompt {
  val builder = PromptBuilderImpl(project)
  existingPrompt?.let { builder.addAll(existingPrompt) }
  val prompt = builder.apply(builderAction).build()
  val usedAnyFiles =
    prompt.messages.any {
      it.chunks.any { chunk -> chunk.filesUsed.isNotEmpty() } ||
        (it is Prompt.Context && it.files.isNotEmpty())
    }
  if (usedAnyFiles) {
    // Enforce the context sharing setting
    check(StudioBot.getInstance().isContextAllowed(project)) {
      "User has not enabled context sharing. This setting must be checked before building a prompt that used any files as context."
    }
  }
  return prompt
}

/** Utility for constructing prompts for ML models. */
interface PromptBuilder {

  interface MessageBuilder {
    /**
     * Adds [str] as text in the message.
     *
     * **NOTE:** It is caller's responsibility to consult `StudioBot.isContextAllowed(project)` and
     * `AiExcludeService` before including any user file content into the string passed to this
     * method.
     *
     * @see com.android.tools.idea.studiobot.StudioBot.isContextAllowed
     * @see com.android.tools.idea.studiobot.AiExcludeService
     */
    fun text(str: String, filesUsed: Collection<VirtualFile>)

    /**
     * Adds [code] as a Markdown formatted code block in the message, with optional [language]
     * specified if it has a Markdown representation.
     *
     * **NOTE:** It is caller's responsibility to consult `StudioBot.isContextAllowed(project)` and
     * `AiExcludeService` before including any user file content into the string passed to this
     * method.
     *
     * @see com.android.tools.idea.studiobot.StudioBot.isContextAllowed
     * @see com.android.tools.idea.studiobot.AiExcludeService
     */
    fun code(code: String, language: Language?, filesUsed: Collection<VirtualFile>)

    /**
     * Adds data of a given type, for multi-modal models.
     *
     * @param mimeType The type of the data.
     * @param data The raw data bytes.
     */
    fun blob(data: ByteArray, mimeType: MimeType, filesUsed: Collection<VirtualFile>)
  }

  interface ContextBuilder {
    /**
     * Adds a file to the context of the prompt. How it ends up being included in the request
     * depends on the [Model] being used. Some model APIs have a dedicated field for including
     * files. Others take a generic string of context, and others still don't accept context as a
     * dedicated argument at all, and expect any context to be included in the user or system
     * messages.
     *
     * The file must be allowed by aiexclude.
     *
     * @param file The file to include in the context.
     * @param isCurrentFile Whether the file is currently open in an active editor.
     * @param selection The current text selection in [file], if any.
     */
    fun virtualFile(file: VirtualFile, isCurrentFile: Boolean = false, selection: TextRange? = null)

    /** Convenience method for adding multiple files at once. */
    fun virtualFiles(files: List<VirtualFile>) = files.forEach { virtualFile(it) }

    /** You can also construct the ContextFiles yourself and pass them directly if desired * */
    fun file(file: Prompt.ContextFile)

    /** Convenience method for adding multiple files at once. */
    fun files(files: List<Prompt.ContextFile>) = files.forEach { file(it) }
  }

  interface UserMessageBuilder : MessageBuilder {
    val project: Project
  }

  interface FunctionsBuilder {
    /**
     * Adds a declaration of a function, for models that support function calling. This structure is
     * based on the Gemini APIs documented here:
     * https://ai.google.dev/gemini-api/docs/function-calling
     *
     * If you provide a function to a [Model] that supports it, and it returns a function call, it
     * will be reflected as a [Content.FunctionCall] in the returned flow.
     */
    fun function(function: Prompt.Function)

    fun functions(functions: List<Prompt.Function>)

    /**
     * Copied from the Gemini docs (https://ai.google.dev/gemini-api/docs/function-calling):
     *
     * You can use the function calling mode to define the execution behavior for function calling.
     * There are three modes available:
     *
     * AUTO: The default model behavior. The model decides to predict either a function call or a
     * natural language response.
     *
     * ANY: The model is constrained to always predict a function call.
     *
     * NONE: The model won't predict a function call. In this case, the model behavior is the same
     * as if you don't pass any function declarations.
     */
    fun setMode(mode: Prompt.FunctionCallingMode)
  }

  fun systemMessage(builderAction: MessageBuilder.() -> Unit)

  fun userMessage(builderAction: UserMessageBuilder.() -> Unit)

  fun modelMessage(builderAction: MessageBuilder.() -> Unit)

  fun context(builderAction: ContextBuilder.() -> Unit)

  /**
   * No prod models currently support function calling. Only the Gemini 1.5 APIs support it. You can
   * verify if a model supports function calling by inspecting its [ModelConfig]. For now, do not
   * build prod features that use function calling.
   */
  @Experimental fun functions(builderAction: FunctionsBuilder.() -> Unit)
}

class MalformedPromptException(message: String) : RuntimeException(message)
