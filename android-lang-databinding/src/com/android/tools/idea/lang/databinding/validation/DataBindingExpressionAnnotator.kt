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
package com.android.tools.idea.lang.databinding.validation

import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbId
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbVisitor
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement


/**
 * This handles annotation in the data binding expressions (inside `@{}`).
 */
class DataBindingExpressionAnnotator : PsiDbVisitor(), Annotator {
  private var holder: AnnotationHolder? = null

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    try {
      this.holder = holder
      element.accept(this)
    }
    finally {
      this.holder = null
    }
  }

  private fun annotateError(element: PsiElement, error: String, vararg args: Any?) {
    holder!!.createErrorAnnotation(element, error.format(*args))
  }

  /**
   * Data binding expressions are called within the context of a parent ViewDataBinding
   * base class. These classes have a bunch of hidden API methods that users can
   * technically call, but since they are hidden (and stripped), we can't use reflection
   * to know what they are. So we whitelist those special methods here.
   * TODO: (b/135638810) Add additional methods here
   */
  private fun isViewDataBindingMethod(name: String) = name == "safeUnbox"

  /**
   * Annotates unresolvable [PsiDbId] with "Cannot find identifier" error.
   *
   * A [PsiDbId] is unresolvable when none of its ancestors has
   * a valid reference.
   *
   * From db.bnf
   *
   * ```
   * fake refExpr ::= expr? '.' id
   * simpleRefExpr ::= id {extends=refExpr elementType=refExpr}
   * qualRefExpr ::= expr '.' id {extends=refExpr elementType=refExpr}
   * functionRefExpr ::= expr '::' id
   * ```
   * if the identifier's parent contains an unresolvable expression, we should annotate
   * the expression instead.
   */
  override fun visitId(id: PsiDbId) {
    super.visitId(id)

    var element = id.parent
    while (element != null) {
      if (element.reference != null) {
        return
      }
      element = element.parent
    }

    when (val parent = id.parent) {
      is PsiDbRefExpr -> {
        val expr = parent.expr

        // Whitelist hidden methods for simpleRefExpr
        if (expr == null) {
          if (isViewDataBindingMethod(id.text)) {
            return
          }
        }
        // Don't annotate this identifier because we're going to annotate the expression
        // part of its parent element instead
        else if (expr.reference == null) {
          return
        }
      }
      is PsiDbFunctionRefExpr -> {
        if (parent.expr.reference == null) {
          return
        }
      }
    }
    annotateError(id, UNRESOLVED_IDENTIFIER, id.text)
  }

  companion object {
    const val UNRESOLVED_IDENTIFIER = "Cannot find identifier '%s'"
  }
}