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
package com.android.tools.idea.lint.quickFixes

import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.intellij.codeInsight.FileModificationService
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Removes an obsolete if-SDK_INT check. This is only handling Kotlin code since for Java
 * we reuse the builtin SimplifyBooleanExpressionFix check.
 */
class RemoveSdkCheckFix(private var removeThen: Boolean) : DefaultLintQuickFix(
  "Remove obsolete SDK version check",
  "Remove obsolete SDK version checks"
) {

  override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
    val condition = findSdkConditional(startElement) ?: return

    if (!FileModificationService.getInstance().preparePsiElementForWrite(startElement)) {
      return
    }

    val ifExpression = PsiTreeUtil.getParentOfType(condition, KtIfExpression::class.java, true)
    if (ifExpression != null && ifExpression.condition == condition && applyToIfExpression(ifExpression)) {
      return
    }
    else if (removeThen) {
      // Replace with true
        condition.replace(KtPsiFactory(condition).createExpression("true"))
    } else {
      // Replace with false
      condition.replace(KtPsiFactory(condition).createExpression("false"))
    }
  }

  private fun applyToIfExpression(ifExpression: KtIfExpression): Boolean {
    val keep =
      if (removeThen)
        ifExpression.then
      else
        ifExpression.`else`
    if (keep != null) {
      val parent = ifExpression.parent ?: return false
      if (keep is KtBlockExpression) {
        var child: PsiElement? = keep.firstChild
        while (child != null) {
          if (child !is TreeElement || !(child.elementType == KtTokens.RBRACE || child.elementType == KtTokens.LBRACE)) {
            parent.addBefore(child, ifExpression)
          }
          child = child.nextSibling
        }
      }
      else {
        parent.addBefore(keep, ifExpression)
      }
    }
    ifExpression.delete()
    return true
  }

  private fun findSdkConditional(start: PsiElement): KtExpression? {
    var current: PsiElement? = start
    while (current != null) {
      val next = PsiTreeUtil.getParentOfType(current, KtBinaryExpression::class.java, false) ?: break
      if (isVersionCheckConditional(next)) {
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
}
