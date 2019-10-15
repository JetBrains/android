/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

internal fun UFile.declaredMethods(): Sequence<UMethod> =
  classes
    .asSequence()
    .flatMap { it.methods.asSequence() }

internal fun UFile.method(name: String): UMethod? =
  declaredMethods()
    .filter { it.name == name }
    .singleOrNull()

/**
 * Extension to run operations on the [Document] associated to the given [PsiFile]
 */
internal fun PsiFile.runOnDocument(runnable: (PsiDocumentManager, Document) -> Unit) {
  val documentManager = PsiDocumentManager.getInstance(project)
  val document = documentManager.getDocument(this)!!

  WriteCommandAction.runWriteCommandAction(project) {
    runnable(documentManager, document)
  }
}

/**
 * Extension to replace the first occurrence of the [find] string to [replace]
 */
internal fun PsiFile.replaceStringOnce(find: String, replace: String) = runOnDocument { documentManager, document ->
  documentManager.commitDocument(document)

  val index = text.indexOf(find)
  assert(index != -1) { "\"$find\" not found in the given file"}

  document.replaceString(index, index + find.length, replace)
  documentManager.commitDocument(document)
}