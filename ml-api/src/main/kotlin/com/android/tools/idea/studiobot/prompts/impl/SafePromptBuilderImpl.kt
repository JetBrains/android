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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
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
    override fun fileContents(file: VirtualFile) {
      val usedFiles = listOf(file)
      text("The contents of the file \"${file.projectRelativePath()}\" are:", usedFiles)
      val language = (file.fileType as? LanguageFileType)?.language
      code(file.readText(), language, usedFiles)
    }

    override fun fileContents(file: PsiFile) {
      val virtualFile = file.viewProvider.virtualFile
      val usedFiles = listOf(virtualFile)
      text("The contents of the file \"${virtualFile.projectRelativePath()}\" are:", usedFiles)
      code(file.text, file.language, usedFiles)
    }

    override fun withReadAction(
      block: SafePromptBuilder.ReadActionUserMessageBuilder.() -> Unit
    ) {
      ReadActionUserMessageBuilderImpl().apply {
        if (ApplicationManager.getApplication().isReadAccessAllowed) block()
        else ReadAction.run<Throwable> { block() }
      }
    }

    private fun VirtualFile.projectRelativePath() = path.removePrefix(project.basePath ?: "")

    inner class ReadActionUserMessageBuilderImpl :
      SafePromptBuilder.UserMessageBuilder by this@UserMessageBuilderImpl,
      SafePromptBuilder.ReadActionUserMessageBuilder {
      @RequiresReadLock
      override fun openFileContents(editor: Editor) {
        val openFile = editor.virtualFile ?: return
        val usedFiles = listOf(openFile)
        val caret = editor.caretModel.primaryCaret
        val filename = openFile.projectRelativePath()
        text("The file \"$filename\" is open.", usedFiles)
        val contents = FileDocumentManager.getInstance().getDocument(openFile)?.text ?: return
        val language = PsiManager.getInstance(project).findFile(openFile)?.language

        val before = contents.subSequence(0, caret.selectionStart).toString()
        val after = contents.subSequence(caret.selectionEnd, contents.length).toString()
        val selectedText = caret.selectedText
        if (selectedText == null) {
          text("The contents before the caret are:", usedFiles)
          code(before, language, usedFiles)
          text("The contents after the caret are:", usedFiles)
          code(after, language, usedFiles)
        } else {
          text("The contents before the selected text are:", usedFiles)
          code(before, language, usedFiles)
          text("The selected text is:", usedFiles)
          code(selectedText, language, usedFiles)
          text("The contents after the selected text are:", usedFiles)
          code(after, language, usedFiles)
        }
      }
    }
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
    checkPromptFormat(
      messages.last() is SafePrompt.UserMessage,
      "Last message in prompt must be a user message.",
    )
    // Check aiexclude rules
    val excludedFiles = excludedFiles()
    if (excludedFiles.isNotEmpty()) {
      throw AiExcludeException(excludedFiles)
    }
    return SafePromptImpl(messages)
  }
}
