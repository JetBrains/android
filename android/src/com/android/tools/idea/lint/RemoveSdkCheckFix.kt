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
package com.android.tools.idea.lint

import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.intellij.codeInsight.FileModificationService
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtIfExpression

/**
 * Removes an obsolete if-SDK_INT check. This is only handling Kotlin code since for Java
 * we reuse the builtin SimplifyBooleanExpressionFix check.
 */
class RemoveSdkCheckFix(var removeThen: Boolean) : LintIdeQuickFix {

  override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
    val ifExpression = findSdkConditional(startElement) ?: return

    if (!FileModificationService.getInstance().preparePsiElementForWrite(startElement)) {
      return
    }

    val keep =
      if (removeThen)
        ifExpression.then
      else
        ifExpression.`else`
    if (keep != null) {
      val parent = ifExpression.parent ?: return
      if (keep is KtBlockExpression) {
        var child: PsiElement? = keep.firstChild
        while (child != null) {
          if (child is TreeElement && (child.elementType == KtTokens.RBRACE || child.elementType == KtTokens.LBRACE)) {
          }
          else {
            parent.addBefore(child, ifExpression)
          }
          child = child.nextSibling
        }
      }
      else {
        parent.addBefore(keep, ifExpression)
      }
    }
    else {
    }
    ifExpression.delete()
  }

  private fun findSdkConditional(start: PsiElement): KtIfExpression? {
    var current: PsiElement = start
    while (current != null) {
      val next = PsiTreeUtil.getParentOfType(current, KtIfExpression::class.java, false) ?: break
      val conditional = next.condition as? PsiElement
      if (conditional != null && isVersionCheckConditional(conditional)) {
        return next
      }

      current = next.parent
    }

    return null
  }

  private fun isVersionCheckConditional(element: PsiElement): Boolean {
    return element.text.contains("SDK_INT")
  }

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean {
    return true
  }

  override fun getName(): String {
    return "Remove obsolete SDK version check"
  }

  override fun getFamilyName(): String? {
    return "Remove obsolete SDK version checks"
  }
}
