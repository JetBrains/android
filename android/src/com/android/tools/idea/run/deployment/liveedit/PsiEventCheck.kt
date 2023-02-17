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
package com.android.tools.idea.run.deployment.liveedit

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

/**
 * A list of unsupported PSI events that we need to capture in the listener for error reporting purpose.
 *
 * Until we have a full CLASSDIFFER, we need to rely on parsing the PSI tree to see if the edit contains a list of changes
 * that are not well-supported. We need to do this right at the PSI event's source since the AST might be further modified
 * before compilation.
 */
enum class UnsupportedPsiEvent {
  IMPORT_DIRECTIVES,
  CONSTRUCTORS,
  FIELD_CHANGES,
}

fun isImportChanges(target: PsiElement?) : Boolean {
  var cur = target
  while(cur != null) {
    when(cur) {
      is KtFunction -> return false
      is KtImportDirective, is KtImportList -> return true;
    }
    cur = cur.parent
  }
  return false
}

fun isClassFieldChanges(target: PsiElement?) : Boolean {
  var cur = target
  var partOfPropertyStatementOrDeclaration = false
  while(cur != null) {
    when(cur) {
      is KtFunction -> return false
      is KtClass -> return partOfPropertyStatementOrDeclaration
      is KtFile -> return partOfPropertyStatementOrDeclaration
      is KtProperty -> {
        partOfPropertyStatementOrDeclaration = true
      }
      is KtObjectDeclaration -> {
        partOfPropertyStatementOrDeclaration = true
      }
    }
    cur = cur.parent
  }
  return false
}

fun isConstructor(target: PsiElement) : Boolean {
  return target is KtPrimaryConstructor || target is KtSecondaryConstructor
}
