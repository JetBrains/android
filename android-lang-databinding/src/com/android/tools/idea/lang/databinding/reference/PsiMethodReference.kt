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

import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * Reference that refers to a [PsiMethod]
 */
internal class PsiMethodReference(element: PsiElement, resolveTo: PsiElement, textRange: TextRange)
  : DbExprReference(element, resolveTo, textRange) {

  constructor(expr: PsiDbCallExpr, method: PsiMethod) :
    this(expr, method, expr.refExpr.id.textRange.shiftRight(-expr.startOffsetInParent))

  constructor(expr: PsiDbRefExpr, method: PsiMethod)
    : this(expr, method, expr.id.textRange.shiftRight(-expr.startOffsetInParent))

  override val resolvedType: PsiModelClass?
    get() {
      val returnType = (resolve() as PsiMethod).returnType
      return if (returnType != null) PsiModelClass(returnType) else null
    }

  override val isStatic: Boolean
    get() = false

  override fun handleElementRename(newElementName: String): PsiElement? {
    val identifier = element.findElementAt(rangeInElement.startOffset) as LeafPsiElement?
    identifier?.rawReplaceWithText(newElementName)
    return identifier
  }
}
