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

import com.android.tools.idea.gemini.LlmPrompt.Role
import com.android.tools.idea.studiobot.AiExcludeException
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * An [LlmPrompt] represents a prompt sent to a (text only) LLM. A prompt typically consists of
 * messages with various [Role]s. Use [buildLlmPrompt] to construct a prompt.
 *
 * Note: This is a small subset of the full API provided inside aiplugin.core. This API is only
 * meant to be used for simple text inference. If you need more features, then consult the README.md
 * file for more info on how to structure your code to get access to the full API.
 */
interface LlmPrompt {
  enum class Role {
    SYSTEM,
    MODEL,
    USER,
  }

  class Message(val role: Role, val text: String, val filesUsed: Collection<VirtualFile>)

  val messages: List<Message>
}

fun LlmPrompt.formatForTests(): String {
  return buildString {
      for (message in messages) {
        append(message.role.name)
        append(message.text)
        appendLine()
        message.filesUsed.forEach { append("<${it.name}>") }
        appendLine()
      }
    }
    .trimEnd()
}

interface LlmPromptBuilder {
  interface MessageBuilder {
    fun text(str: String, filesUsed: Collection<VirtualFile>)

    fun code(code: String, language: Language, filesUsed: Collection<VirtualFile>)
  }

  fun systemMessage(builder: MessageBuilder.() -> Unit)

  fun userMessage(builder: MessageBuilder.() -> Unit)

  fun modelMessage(builder: MessageBuilder.() -> Unit)
}

/**
 * A builder for an [LlmPrompt]. A typical usage would look like:
 * ```
 * buildLlmPrompt(project) {
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
 * }
 *
 * If the prompt contains any snippets taken from user code, then
 * the following two checks must hold:
 *   1. GeminiPluginApi.getInstance().isContextAllowed(project) should be true.
 *   2. The file must not be excluded via .aiexclude.
 * ```
 */
fun buildLlmPrompt(project: Project, builder: LlmPromptBuilder.() -> Unit): LlmPrompt {
  val messages = LlmPromptBuilderImpl(project).apply(builder).messages
  return object : LlmPrompt {
    override val messages = messages
  }
}

internal class LlmPromptBuilderImpl(private val project: Project) : LlmPromptBuilder {
  val messages = mutableListOf<LlmPrompt.Message>()

  class MessageBuilderImpl(val project: Project) : LlmPromptBuilder.MessageBuilder {
    val sb = StringBuilder()
    val files = mutableSetOf<VirtualFile>()

    override fun text(str: String, filesUsed: Collection<VirtualFile>) = content(str, filesUsed)

    override fun code(code: String, language: Language, filesUsed: Collection<VirtualFile>) =
      content(mdFormat(code, language), filesUsed)

    private fun content(text: String, filesUsed: Collection<VirtualFile>) {
      if (filesUsed.isNotEmpty()) {
        check(GeminiPluginApi.getInstance().isContextAllowed(project)) {
          "User has not enabled context sharing. This setting must be checked before building a prompt that used any files as context."
        }
      }
      assertAiExcludeSafe(filesUsed)
      sb.appendLine()
      sb.append(text)
      files.addAll(filesUsed)
    }

    private fun mdFormat(code: String, language: Language) =
      """
        |```${language.displayName.lowercase()}
        |$code
        |```
      """
        .trimMargin()

    private fun assertAiExcludeSafe(files: Collection<VirtualFile>) {
      // Check aiexclude rules *in smart mode* so that we don't run into
      // ExclusionStatus.INDETERMINATE
      val excludedFiles =
        DumbService.getInstance(project).runReadActionInSmartMode<List<VirtualFile>> {
          files.filter { GeminiPluginApi.getInstance().isFileExcluded(project, it) }
        }
      if (excludedFiles.isNotEmpty()) {
        throw AiExcludeException(excludedFiles)
      }
    }

    fun build(role: Role): LlmPrompt.Message = LlmPrompt.Message(role, sb.toString(), files)
  }

  override fun systemMessage(builder: LlmPromptBuilder.MessageBuilder.() -> Unit) {
    message(Role.SYSTEM, builder)
  }

  override fun userMessage(builder: LlmPromptBuilder.MessageBuilder.() -> Unit) {
    message(Role.USER, builder)
  }

  override fun modelMessage(builder: LlmPromptBuilder.MessageBuilder.() -> Unit) {
    message(Role.MODEL, builder)
  }

  private fun message(role: Role, builder: LlmPromptBuilder.MessageBuilder.() -> Unit) {
    messages.add(MessageBuilderImpl(project).apply(builder).build(role))
  }
}
