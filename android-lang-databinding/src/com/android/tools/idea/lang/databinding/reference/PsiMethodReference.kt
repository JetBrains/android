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

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * Reference that refers to a [PsiMethod]
 */
internal class PsiMethodReference(element: PsiElement, resolveTo: PsiElement, textRange: TextRange)
  : DbExprReference(element, resolveTo, textRange) {

  constructor(expr: PsiDbCallExpr, method: PsiMethod) :
    this(expr, method, expr.refExpr.id.textRange.shiftLeft(expr.textOffset))

  constructor(expr: PsiDbRefExpr, method: PsiMethod)
    : this(expr, method, expr.id.textRange.shiftLeft(expr.textOffset))

  constructor(expr: PsiDbFunctionRefExpr, method: PsiMethod)
    : this(expr, method, expr.id.textRange.shiftLeft(expr.textOffset))

  override val resolvedType: PsiModelClass?
    get() = (resolve() as? PsiMethod)?.returnType?.let {
      PsiModelClass(it, DataBindingMode.fromPsiElement(element))
    }

  override val isStatic: Boolean
    get() = false

  override fun handleElementRename(newElementName: String): PsiElement? {
    val identifier = element.findElementAt(rangeInElement.startOffset) as LeafPsiElement?
    val stripped = (resolve() as? PsiMethod)?.let { stripPrefixFromMethod(it, newElementName) } ?: newElementName
    identifier?.rawReplaceWithText(stripped)
    return identifier
  }

  companion object {
    /**
     * Given a method and its new name, return the method name with the prefix stripped,
     * or {@code null} otherwise.
     * TODO(131227177): Refactor this logic to a location shared with BrUtil
     */
    fun stripPrefixFromMethod(method: PsiMethod, newName: String) = when {
      isGetter(method, newName) -> newName.substring("get".length).decapitalize()
      isBooleanGetter(method, newName) -> newName.substring("is".length).decapitalize()
      else -> null
    }

    private fun isGetter(psiMethod: PsiMethod, name: String) =
      matchesMethodPattern(psiMethod, name, "get", 0) { type -> PsiType.VOID != type }

    private fun isBooleanGetter(psiMethod: PsiMethod, name: String) =
      matchesMethodPattern(psiMethod, name, "is", 0) { type -> PsiType.BOOLEAN == type }

    private fun matchesMethodPattern(psiMethod: PsiMethod,
                                     name: String,
                                     prefix: String,
                                     parameterCount: Int,
                                     returnTypePredicate: (PsiType) -> Boolean): Boolean {
      return name.startsWith(prefix) &&
             Character.isJavaIdentifierStart(name[prefix.length]) &&
             psiMethod.parameterList.parametersCount == parameterCount &&
             psiMethod.returnType?.let { returnTypePredicate.invoke(it) } == true
    }
  }
}
