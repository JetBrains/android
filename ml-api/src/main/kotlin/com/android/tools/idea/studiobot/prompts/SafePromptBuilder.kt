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

import com.android.tools.idea.studiobot.prompts.impl.SafePromptBuilderImpl
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Use this builder to construct prompts for Studio Bot APIs by specifying
 * a series of messages. Each part of the prompt must declare which files
 * from the user's project, if any, it uses.
 *
 * No files may be used if the context sharing setting is disabled:
 * check it with StudioBot.isContextAllowed()
 *
 * Additionally, Each file must be allowed by aiexclude. Check files using
 * AiExcludeService.isFileExcluded. If a file is not allowed, an
 * AiExcludeException will be thrown.
 *
 * A prompt consists of a series of messages.
 * There are three types of messages currently: system, user, and model.
 *
 * A system message at the start of the prompt can be used to provide
 * a preamble - a set of instructions that the model is supposed to
 * follow in the conversation.
 *
 * A user message is a message sent by the user, while a model message
 * is one that the model responded with.
 *
 * The last user message is what the model is supposed to respond to
 * for this prompt/turn of conversation.
 *
 * Prompts are subject the following constraints; a [MalformedPromptException]
 * will be thrown if any aren't followed:
 *  * Prompts must contain at least one message.
 *  * The last message must be a user message.
 *  * There can be at most one system message, and it must be the first message.
 *
 * Use [SafePromptBuilder.MessageBuilder.text] to add plaintext to a message,
 * and [SafePromptBuilder.MessageBuilder.code] to add a formatted
 * code block with the given language.
 *
 * Example usage:
 * ```
 * buildPrompt {
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
inline fun buildPrompt(project: Project, builderAction: SafePromptBuilder.() -> Unit): SafePrompt {
  return SafePromptBuilderImpl(project).apply(builderAction).build()
}

/**
 * Utility for constructing prompts for Studio Bot.
 */
interface SafePromptBuilder {
  val messages: List<SafePrompt.Message>

  interface MessageBuilder {
    /**
     * Adds [str] as text in the message.
     */
    fun text(str: String, filesUsed: Collection<VirtualFile>)

    /**
     * Adds [code] as a Markdown formatted code block in the message,
     * with optional [language] specified if it has a Markdown representation.
     */
    fun code(code: String, language: Language?, filesUsed: Collection<VirtualFile>)
  }

  interface UserMessageBuilder : MessageBuilder {
    /** Adds information about [file], including its contents. */
    fun fileContents(file: VirtualFile)

    /** Adds information about [file], including its contents. */
    fun fileContents(file: PsiFile)

    /** Starts a read action allowing further query elements that require read access. */
    fun withReadAction(block: ReadActionUserMessageBuilder.() -> Unit)
  }

  interface ReadActionUserMessageBuilder : UserMessageBuilder {
    /** Adds the contents from the file currently open in [editor] to the query. */
    @RequiresReadLock
    fun openFileContents(editor: Editor)
  }

  fun systemMessage(builderAction: MessageBuilder.() -> Unit)

  fun userMessage(builderAction: UserMessageBuilder.() -> Unit)

  fun modelMessage(builderAction: MessageBuilder.() -> Unit)
}

class MalformedPromptException(message: String): RuntimeException(message)