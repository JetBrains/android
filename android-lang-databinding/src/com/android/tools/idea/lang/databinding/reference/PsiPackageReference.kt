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

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference

/**
 * Reference that refers to a [PsiPackage]
 */
internal class PsiPackageReference(private val element: PsiElement, private val target: PsiPackage) : PsiReference {
  private val textRange: TextRange = element.textRange.shiftRight(-element.startOffsetInParent)

  override fun getElement(): PsiElement {
    return element
  }

  override fun getRangeInElement(): TextRange {
    return textRange
  }

  override fun resolve(): PsiPackage? {
    return target
  }

  override fun getCanonicalText(): String {
    return element.text
  }

  override fun handleElementRename(newElementName: String): PsiElement? {
    return null
  }

  override fun bindToElement(element: PsiElement): PsiElement? {
    return null
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return element.manager.areElementsEquivalent(resolve(), element)
  }

  override fun isSoft(): Boolean {
    return false
  }
}
