/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.intentions

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.core.insertMembersAfter
import org.jetbrains.kotlin.idea.core.moveCaretIntoGeneratedElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration

internal fun <T : KtDeclaration> insertMembersAfterAndReformat(
  editor: Editor?,
  classOrObject: KtClassOrObject,
  members: Collection<T>,
  anchor: PsiElement? = null,
  getAnchor: (KtDeclaration) -> PsiElement? = { null },
): List<T> {
  val codeStyleManager = CodeStyleManager.getInstance(classOrObject.project)
  return runWriteAction {
    val insertedMembersElementPointers = insertMembersAfter(editor, classOrObject, members, anchor, getAnchor)
    val firstElement = insertedMembersElementPointers.firstOrNull() ?: return@runWriteAction emptyList()

    fun insertedMembersElements() = insertedMembersElementPointers.mapNotNull { it.element }

    for (added in insertedMembersElements()) {
      ShortenReferencesFacility.getInstance().shorten(added)
    }
    if (editor != null) {
      firstElement.element?.let { moveCaretIntoGeneratedElement(editor, it) }
    }

    insertedMembersElementPointers.onEach { it.element?.let { element -> codeStyleManager.reformat(element) } }
    insertedMembersElements()
  }
}

internal fun <T : KtDeclaration> insertMembersAfterAndReformat(
  editor: Editor?,
  classOrObject: KtClassOrObject,
  declaration: T,
  anchor: PsiElement? = null
): T {
  return insertMembersAfterAndReformat(editor, classOrObject, listOf(declaration), anchor).single()
}