/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine
import java.io.File

object ReformatUtil {
  @JvmStatic
  fun reformatRearrangeAndSave(project: Project, files: Iterable<File>) {
    WriteCommandAction.runWriteCommandAction(project) {
      files.asSequence()
        .filter { it.isFile }
        // We skip gradlew files, which IntelliJ recognizes as shell files and offers to install the bash IDE plugin. These files are
        // created with the right formatting by the templates and we don't want the balloon on startup.
        .filterNot { it.name.startsWith("gradlew") }
        .forEach {
          val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(it)!!
          reformatAndRearrange(project, virtualFile, keepDocumentLocked = true)
          FileDocumentManager.getInstance().run {
            getDocument(virtualFile)?.let { document -> saveDocument(document) }
          }
        }
    }
  }

  /**
   * Reformats and rearranges the part of the File concerning the PsiElement received
   *
   * @param project    The project which contains the given element
   * @param psiElement The element to be reformated and rearranged
   */
  @JvmStatic
  fun reformatAndRearrange(project: Project, psiElement: PsiElement) =
    reformatAndRearrange(project, psiElement.containingFile.virtualFile, psiElement, true)

  /**
   * Reformats and rearranges the file. By default reformats entire file, but may reformat part of it
   *
   * Note: reformatting the PSI file requires that this be wrapped in a write command.
   *
   * @param project            The project which contains the given element
   * @param virtualFile        Virtual file to be reformatted and rearranged
   * @param psiElement         The element in the file to be reformatted and rearranged
   * @param keepDocumentLocked True if the document will still be modified in the same write action
   */
  @JvmStatic
  @JvmOverloads
  fun reformatAndRearrange(project: Project,
                           virtualFile: VirtualFile,
                           psiElement: PsiElement? = null,
                           keepDocumentLocked: Boolean = false) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                   ?: return // The file could be a binary file with no editing support...

    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(document)

    val psiFile = psiDocumentManager.getPsiFile(document) ?: return

    var textRange = if (psiElement == null) psiFile.textRange else psiElement.textRange
    CodeStyleManager.getInstance(project).reformatRange(psiFile, textRange.startOffset, textRange.endOffset)

    // The textRange of psiElement in the file can change after reformatting
    textRange = if (psiElement == null) psiFile.textRange else psiElement.textRange
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
    project.getService(ArrangementEngine::class.java).arrange(psiFile, setOf(textRange))

    if (keepDocumentLocked) {
      psiDocumentManager.commitDocument(document)
      PsiDocumentManager.getInstance(project).reparseFiles(listOf(virtualFile), true)
    }
  }
}