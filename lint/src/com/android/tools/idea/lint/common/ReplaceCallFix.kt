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
package com.android.tools.idea.lint.common

import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ReplaceCallFix(private val mySuggest: String, element: PsiElement) :
  PsiBasedModCommandAction<PsiElement>(element) {
  private val methodName: String
    get() {
      val start = if (mySuggest.startsWith("#")) 1 else 0
      var parameters = mySuggest.indexOf('(', start)
      if (parameters == -1) {
        parameters = mySuggest.length
      }
      return mySuggest.substring(start, parameters).trim()
    }

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    Presentation.of("Call $methodName instead")

  override fun getFamilyName() = "ReplaceCallFix"

  @Suppress("UnstableApiUsage")
  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    return ModCommand.psiUpdate(element) { e, _ ->
      when (element.language) {
        JavaLanguage.INSTANCE -> handleJava(e)
        KotlinLanguage.INSTANCE -> handleKotlin(e)
      }
    }
  }

  private fun handleJava(element: PsiElement) {
    val methodCall =
      PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false) ?: return
    val file = methodCall.containingFile ?: return
    val document = file.fileDocument
    val methodExpression = methodCall.methodExpression
    val referenceNameElement = methodExpression.referenceNameElement ?: return
    val range = referenceNameElement.textRange ?: return

    // Also need to insert a message parameter
    // Currently hardcoded for the check*Permission to enforce*Permission code path. It's
    // tricky to figure out in general how to map existing parameters to new
    // parameters. Consider using MethodSignatureInsertHandler.
    val name = methodName
    if (name.startsWith("enforce") && name.endsWith("Permission")) {
      val referenceName = methodExpression.referenceName
      if (referenceName != null && referenceName.startsWith("check")) {
        val argumentList = methodCall.argumentList
        val offset = argumentList.textOffset + argumentList.textLength - 1
        document.insertString(offset, ", \"TODO: message if thrown\"")
      }
    }

    // Replace method call
    document.replaceString(range.startOffset, range.endOffset, name)
  }

  private fun handleKotlin(element: PsiElement) {
    val methodCall =
      PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return
    val methodExpression = methodCall.calleeExpression
    if (methodExpression is KtNameReferenceExpression) {
      val identifier: PsiElement? = methodExpression.getIdentifier()
      if (identifier != null) {
        val range = identifier.textRange
        val file = methodCall.containingFile ?: return
        val document = file.fileDocument

        // Also need to insert a message parameter
        // Currently hardcoded for the check*Permission to enforce*Permission code path. It's
        // tricky to figure out in general how to map existing parameters to new
        // parameters. Consider using MethodSignatureInsertHandler.
        val name = methodName
        if (name.startsWith("enforce") && name.endsWith("Permission")) {
          val referencedName = methodExpression.getReferencedName()
          if (referencedName.startsWith("check")) {
            methodCall.valueArgumentList?.textRange?.let { range: TextRange ->
              val offset = range.endOffset - 1
              document.insertString(offset, ", \"TODO: message if thrown\"")
            }
          }
        }

        // Replace method call
        document.replaceString(range.startOffset, range.endOffset, name)
      }
    }
  }
}
