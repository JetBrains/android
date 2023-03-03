/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.idea.lint.common.AndroidQuickfixContexts.ContextType
import com.android.tools.idea.lint.common.AndroidQuickfixContexts.EditorContext
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

class CreateFileQuickFix(
  private val myFile: File,
  private val myText: String?,
  private val myBinary: ByteArray?,
  private val mySelectPattern: String?,
  private val myFormat: Boolean,
  name: String,
  familyName: String?
) : DefaultLintQuickFix(name, familyName) {
  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context
  ) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) {
      // The newly created file is never the current preview one we're looking at
      return
    }
    val project = startElement.project
    if (LocalFileSystem.getInstance().findFileByIoFile(myFile) != null && !isUnitTestMode()) {
      if (
        Messages.showYesNoDialog(
          project,
          "${myFile.name} already exists; do you want to replace it?",
          "Replace File",
          null
        ) == Messages.YES
      ) {
        createFile(project, context)
      }
      return
    }

    createFile(project, context)
  }

  private fun createFile(project: Project, context: AndroidQuickfixContexts.Context) {
    try {
      val parent = VfsUtil.createDirectoryIfMissing(myFile.parentFile.path) ?: return

      // Deletion?
      if (myBinary == null && myText == null) {
        VfsUtil.findFileByIoFile(myFile, false)?.delete(this)
        return
      }
      val newFile = parent.createChildData(this, myFile.name)

      // Binary file?
      if (myBinary != null) {
        newFile.setBinaryContent(myBinary)
        // no formatting or selection
        return
      }

      // Text file
      myText!!
      newFile.setBinaryContent(myText.toByteArray(Charsets.UTF_8))

      // Format
      if (myFormat) {
        PsiManager.getInstance(project).findFile(newFile)?.let {
          CodeStyleManager.getInstance(project).reformat(it)
        }
      }

      // Open editor & select?
      if (context !is EditorContext) {
        return
      }

      val manager = FileEditorManager.getInstance(project)
      manager.openFile(newFile, true, true)

      if (mySelectPattern != null) {
        val pattern = Pattern.compile(mySelectPattern)
        val matcher = pattern.matcher(myText)
        if (matcher.find()) {
          val selectStart: Int
          val selectEnd: Int
          if (matcher.groupCount() > 0) {
            selectStart = matcher.start(1)
            selectEnd = matcher.end(1)
          } else {
            selectStart = matcher.start()
            selectEnd = matcher.end()
          }
          val editor = context.editor
          editor.selectionModel.setSelection(selectStart, selectEnd)
        }
      }
    } catch (e: IOException) {
      Logger.getInstance(CreateFileQuickFix::class.java).error(e)
    }
  }

  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: ContextType
  ): Boolean = true
}
