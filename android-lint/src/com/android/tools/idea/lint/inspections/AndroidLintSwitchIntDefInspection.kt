/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections

import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.ModCommandLintQuickFix
import com.android.tools.lint.checks.AnnotationDetector
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.Companion.getStringList
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression

class AndroidLintSwitchIntDefInspection :
  AndroidLintInspectionBase(
    message("android.lint.inspections.switch.int.def"),
    AnnotationDetector.SWITCH_TYPE_DEF,
  ) {
  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    fixData: LintFix?,
  ): Array<LintIdeQuickFix> {
    val missingCases = getStringList(fixData, AnnotationDetector.KEY_CASES)
    if (!missingCases.isNullOrEmpty()) {
      return arrayOf(
        ModCommandLintQuickFix(
          object : PsiBasedModCommandAction<PsiElement>(startElement) {

            override fun getFamilyName() = "AddMissingIntDefFix"

            @Suppress("DialogTitleCapitalization")
            override fun getPresentation(context: ActionContext, element: PsiElement) =
              Presentation.of("Add Missing @IntDef Constants")

            override fun perform(context: ActionContext, element: PsiElement): ModCommand {
              val project = element.project

              if (element.language == JavaLanguage.INSTANCE) {
                val switchStatement =
                  element.parent as? PsiSwitchStatement ?: return ModCommand.nop()
                val factory = JavaPsiFacade.getElementFactory(project)

                @Suppress("UnstableApiUsage")
                return ModCommand.psiUpdate(switchStatement) { switch, _ ->
                  val body = switch.body ?: return@psiUpdate
                  val anchor = body.lastChild
                  for (case in missingCases) {
                    // The list we get from lint is using raw formatting, surrounding constants like
                    // `this`
                    val constant = TextFormat.RAW.convertTo(case, TextFormat.TEXT)
                    val parent = anchor.parent
                    val caseStatement = factory.createStatementFromText("case $constant:", anchor)
                    parent.addBefore(caseStatement, anchor)
                    val breakStatement = factory.createStatementFromText("break;", anchor)
                    parent.addBefore(breakStatement, anchor)
                  }
                }
              } else if (element.language == KotlinLanguage.INSTANCE) {
                // Kotlin
                val whenExpression =
                  PsiTreeUtil.getParentOfType(startElement, KtWhenExpression::class.java, false)
                    ?: return ModCommand.nop()
                val factory = KtPsiFactory(project)

                @Suppress("UnstableApiUsage")
                return ModCommand.psiUpdate(whenExpression) { whenExpr, _ ->
                  val anchor = whenExpr.closeBrace
                  for (case in missingCases) {
                    // The list we get from lint is using raw formatting, surrounding constants like
                    // `this`
                    val constant = TextFormat.RAW.convertTo(case, TextFormat.TEXT)
                    val caseStatement = factory.createWhenEntry("$constant-> { TODO() }")
                    (whenExpr as PsiElement).addBefore(caseStatement, anchor)

                    ShortenReferencesFacility.Companion.getInstance().shorten(whenExpr)
                  }
                }
              }
              return ModCommand.nop()
            }
          }
        )
      )
    }

    return super.getQuickFixes(startElement, endElement, message, fixData)
  }
}
