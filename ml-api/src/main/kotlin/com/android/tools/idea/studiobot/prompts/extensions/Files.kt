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
package com.android.tools.idea.studiobot.prompts.extensions

import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.mimetype.fromLanguage
import com.android.tools.idea.studiobot.prompts.PromptBuilder.UserMessageBuilder
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

private fun VirtualFile.projectRelativePath(project: Project) =
  path.removePrefix(project.basePath ?: "")

/**
 * Adds information about [file], including its contents.
 *
 * DISCLAIMER: This is a convenience method for adding higher-level context to user messages. You
 * should not view the low-level formatting of these messages as authoritative in any way, they are
 * simply _one_ way to do it. If you care about your prompts performing optimally, you should build
 * and evaluate for your use case, potentially using the lower-level APIs in
 * [com.android.tools.idea.studiobot.prompts.PromptBuilder.MessageBuilder] directly instead.
 */
fun UserMessageBuilder.fileContents(file: VirtualFile, lang: MimeType? = null) {
  val usedFiles = listOf(file)
  text("The contents of the file \"${file.projectRelativePath(project)}\" are:", usedFiles)
  val language =
    lang ?: (file.fileType as? LanguageFileType)?.language?.let { MimeType.fromLanguage(it) }
  code(file.readText(), language, usedFiles)
}

/**
 * Adds information about [file], including its contents.
 *
 * DISCLAIMER: This is a convenience method for adding higher-level context to user messages. You
 * should not view the low-level formatting of these messages as authoritative in any way, they are
 * simply _one_ way to do it. If you care about your prompts performing optimally, you should build
 * and evaluate for your use case, potentially using the lower-level APIs in
 * [com.android.tools.idea.studiobot.prompts.PromptBuilder.MessageBuilder] directly instead.
 */
fun UserMessageBuilder.fileContents(file: PsiFile) {
  fileContents(file.viewProvider.virtualFile, file.language?.let { MimeType.fromLanguage(it) })
}

/** Starts a read action allowing further query elements that require read access. */
fun UserMessageBuilder.withReadAction(block: ReadActionUserMessageBuilder.() -> Unit) {
  ReadActionUserMessageBuilderImpl(this).apply {
    if (ApplicationManager.getApplication().isReadAccessAllowed) block()
    else ReadAction.run<Throwable> { block() }
  }
}

interface ReadActionUserMessageBuilder : UserMessageBuilder {
  /**
   * Adds the contents from the file currently open in [editor] to the query.
   *
   * DISCLAIMER: This is a convenience method for adding higher-level context to user messages. You
   * should not view the low-level formatting of these messages as authoritative in any way, they
   * are simply _one_ way to do it. If you care about your prompts performing optimally, you should
   * build and evaluate for your use case, potentially using the lower-level APIs in
   * [com.android.tools.idea.studiobot.prompts.PromptBuilder.MessageBuilder] directly instead.
   */
  @RequiresReadLock fun openFileContents(editor: Editor)
}

private class ReadActionUserMessageBuilderImpl(userMessageBuilder: UserMessageBuilder) :
  UserMessageBuilder by userMessageBuilder, ReadActionUserMessageBuilder {
  @RequiresReadLock
  override fun openFileContents(editor: Editor) {
    val openFile = editor.virtualFile ?: return
    val usedFiles = listOf(openFile)
    val caret = editor.caretModel.primaryCaret
    val filename = openFile.projectRelativePath(project)
    text("The file \"$filename\" is open.", usedFiles)
    val contents = FileDocumentManager.getInstance().getDocument(openFile)?.text ?: return
    val language =
      PsiManager.getInstance(project).findFile(openFile)?.language?.let {
        MimeType.fromLanguage(it)
      }

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
