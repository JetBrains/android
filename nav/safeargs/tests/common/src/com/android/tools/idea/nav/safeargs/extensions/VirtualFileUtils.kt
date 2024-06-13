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
package com.android.tools.idea.nav.safeargs.extensions

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

fun VirtualFile.replaceWithoutSaving(oldString: String, newString: String, project: Project) {
  with(FileDocumentManager.getInstance().getDocument(this)!!) {
    val text = charsSequence.toString().replace(oldString, newString)
    setText(text)
    PsiDocumentManager.getInstance(project).commitAndUnblockDocument(this)
  }
}

fun VirtualFile.replaceWithSaving(oldString: String, newString: String, project: Project) {
  with(FileDocumentManager.getInstance().getDocument(this)!!) {
    val text = charsSequence.toString().replace(oldString, newString)
    setText(text)
    PsiDocumentManager.getInstance(project).commitAndUnblockDocument(this)
    FileDocumentManager.getInstance().saveDocument(this)
  }
}

fun VirtualFile.setText(newString: String, project: Project) {
  with(FileDocumentManager.getInstance().getDocument(this)!!) {
    setText(newString)
    PsiDocumentManager.getInstance(project).commitAndUnblockDocument(this)
    FileDocumentManager.getInstance().saveDocument(this)
  }
}

private fun PsiDocumentManager.commitAndUnblockDocument(document: Document) {
  commitDocument(document)
  doPostponedOperationsAndUnblockDocument(document)
}
