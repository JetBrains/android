/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.utils.join

class AddSuperCallFix(element: PsiElement, private val superMethod: PsiMethod) :
  PsiBasedModCommandAction<PsiElement>(element) {

  override fun getFamilyName() = "AddSuperCallFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    Presentation.of("Add super call")

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val project = element.project

    if (element.language === JavaLanguage.INSTANCE) {
      val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
      if (method == null || method.isConstructor) {
        return ModCommand.nop()
      }

      val factory = JavaPsiFacade.getInstance(project).elementFactory
      // Create the statement to be added as the first one in the method.
      // e.g. super.onCreate(savedInstanceState);
      val superStatement =
        factory.createStatementFromText(buildSuperStatement(method, superMethod), null)

      @Suppress("UnstableApiUsage")
      return ModCommand.psiUpdate(method) { methodCopy, _ ->
        var body = methodCopy.body
        if (body != null) {
          val statements = body.statements
          if (statements.isNotEmpty()) {
            body.addBefore(superStatement, statements[0])
          } else {
            // Remove whitespace in the body that does not have statements
            // Only removed if the body has no comments.
            val whiteSpace = PsiTreeUtil.getChildOfType(body, PsiWhiteSpace::class.java)
            if (whiteSpace != null && whiteSpace.text.startsWith("\n\n")) {
              body = body.replace(factory.createCodeBlock()) as PsiCodeBlock
            }
            body.add(superStatement)
          }
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(body)
        }
      }
    } else if (element.language === KotlinLanguage.INSTANCE) {

      val factory = KtPsiFactory(project)
      val method =
        PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java) ?: return ModCommand.nop()

      val superStatement = factory.createExpression(buildSuperStatement(method, superMethod))

      @Suppress("UnstableApiUsage")
      return ModCommand.psiUpdate(method) { methodCopy, _ ->
        var bodyBlock = methodCopy.bodyBlockExpression
        if (bodyBlock == null) {
          val body = methodCopy.bodyExpression
          if (body != null) {
            var eq: PsiElement? = null
            var prev = body.prevSibling
            while (prev != null) {
              if (prev is TreeElement && (prev as TreeElement).elementType === KtTokens.EQ) {
                eq = prev
                break
              }
              prev = prev.prevSibling
            }
            val parent = body.parent
            bodyBlock = factory.createSingleStatementBlock(body, null, null)
            body.delete()
            eq?.delete()

            bodyBlock = parent.add(bodyBlock) as KtBlockExpression
          }
        }
        if (bodyBlock != null) {
          val lBrace = bodyBlock.lBrace
          if (lBrace != null) {
            bodyBlock.addAfter(superStatement, lBrace)
          } else {
            val statements = bodyBlock.statements
            if (statements.isNotEmpty()) {
              bodyBlock.addBefore(superStatement, statements[0])
            }
          }
          ShortenReferencesFacility.Companion.getInstance().shorten(bodyBlock)
        }
      }
    }
    return ModCommand.nop()
  }

  private fun buildSuperStatement(method: PsiMethod, superMethod: PsiMethod): String {
    return buildString {
      val containingClass = superMethod.containingClass
      if (containingClass?.isInterface == true) { append("${containingClass.qualifiedName}.") }

      append("super.${method.name}(")
      append(join(method.parameterList.parameters.map { it.name }, ","))
      append(");")
    }
  }

  private fun buildSuperStatement(method: KtNamedFunction, superMethod: PsiMethod): String {
    val qualifiedClass = analyze(method) {
      val ktCallableSymbol = method.getSymbol() as? KtCallableSymbol ?: return@analyze null
      val choices = ktCallableSymbol.getDirectlyOverriddenSymbols()
      if (choices.size > 1) {
        // We need to disambiguate the call
        superMethod.containingClass?.let {
          return@analyze it.qualifiedName
        }
      }
      return@analyze null
    }

    return buildString {
      append("super")
      qualifiedClass?.let { append("<${it}>") }
      append(".${method.name}(")
      append(join(method.valueParameters.map { it.name ?: "" }, ","))
      append(")")
    }
  }
}
