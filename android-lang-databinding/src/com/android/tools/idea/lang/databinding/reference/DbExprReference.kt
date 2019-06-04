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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

/**
 * A base class for references found within a data binding expression.
 */
internal abstract class DbExprReference(private val psiElement: PsiElement,
                               private val resolveTo: PsiElement?,
                               private val textRange: TextRange = TextRange(0, psiElement.textLength))
  : ModelClassResolvable, PsiReference {

  override fun getElement(): PsiElement {
    return psiElement
  }

  override fun getRangeInElement(): TextRange {
    return textRange
  }

  override fun resolve(): PsiElement? {
    return resolveTo
  }

  override fun getCanonicalText(): String {
    return psiElement.text
  }

  override fun handleElementRename(newElementName: String): PsiElement? {
    return null
  }

  override fun bindToElement(element: PsiElement): PsiElement? {
    return null
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return psiElement.manager.areElementsEquivalent(resolve(), element)
  }

  override fun isSoft(): Boolean {
    return false
  }
}
