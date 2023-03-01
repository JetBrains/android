/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.aa.intentions

import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposableFunction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * K2 version of [ComposeUnresolvedFunctionFixContributor].
 *
 * Creates quick fix for an unresolved reference inside a Composable function.
 *
 * Created action creates new function with @Composable annotation.
 *
 * Example:
 * For
 *
 * @Composable
 * fun myComposable() {
 *  <caret>newFunction()
 * }
 *
 * creates
 *
 * @Composable
 * fun newFunction() {
 *  TODO("Not yet implemented")
 * }
 *
 * b/267429486: This quickfix should make use of [CreateCallableFromUsageFix]
 * when that machinery is available on K2. For now, this implementation will
 * e.g. always extract the newFunction as a sibling to the calling compose
 * Function, and have fewer smarts in terms of parameter names and types.
 */
class ComposeCreateComposableFunctionQuickFix(
  element: KtCallExpression,
  private val newFunction: KtNamedFunction,
  private val sibling: KtNamedFunction,
): QuickFixActionBase<KtCallExpression>(element) {

  override fun getFamilyName(): String =
    KotlinBundle.message("fix.create.from.usage.family")

  override fun getText(): String =
    ComposeBundle.message("create.composable.function") + " '${newFunction.name}'"

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    sibling.addSiblingAfter(newFunction)
  }

  companion object {

    val factory: KotlinDiagnosticFixFactory<KtFirDiagnostic.UnresolvedReference> =
      diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) { diagnostic ->
        listOfNotNull(createComposableFunctionQuickFixIfApplicable(diagnostic))
      }

    private fun KtAnalysisSession.createComposableFunctionQuickFixIfApplicable(
      diagnostic: KtFirDiagnostic.UnresolvedReference
    ): ComposeCreateComposableFunctionQuickFix? {
      val unresolvedCall = diagnostic.psi.parent as? KtCallExpression ?: return null
      val parentFunction = unresolvedCall.getStrictParentOfType<KtNamedFunction>() ?: return null
      if (!isComposableFunction(parentFunction)) return null

      val unresolvedName = (unresolvedCall.calleeExpression as? KtSimpleNameExpression)?.getReferencedName() ?: return null
      if (unresolvedName.isBlank() || !unresolvedName[0].isUpperCase()) return null

      val fullCallExpression = unresolvedCall.getQualifiedExpressionForSelectorOrThis()
      val container = fullCallExpression.getExtractionContainers().firstOrNull() ?: return null

      val returnType = guessReturnType(fullCallExpression)
      if (!returnType.isUnit) return null

      val newFunction = buildNewComposableFunction(unresolvedCall, unresolvedName, container)
      return ComposeCreateComposableFunctionQuickFix(unresolvedCall, newFunction, parentFunction)
    }

    /**
     * Budget-version of [CreateCallableFromUsageFix]: Constructs a plain
     * function annotated with `@Composable`: infers (type) parameters from
     * [unresolvedCall].
     *
     * See b/267429486.
     */
    private fun KtAnalysisSession.buildNewComposableFunction(
      unresolvedCall: KtCallExpression,
      unresolvedName: String,
      container: KtElement,
    ): KtNamedFunction =
      KtPsiFactory(container).createFunction(
        KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
          modifier("@$COMPOSABLE_ANNOTATION_NAME")
          typeParams(unresolvedCall.typeArguments.mapIndexed { index, _ -> "T$index" })
          name(unresolvedName)
          unresolvedCall.valueArguments.forEachIndexed { index, arg ->
            val type = arg.getArgumentExpression()?.getKtType() ?: builtinTypes.ANY
            val name = arg.getArgumentName()?.referenceExpression?.getReferencedName() ?: "x$index"
            param(name, type.render(KtTypeRendererOptions.SHORT_NAMES))
          }
          noReturnType()
          blockBody("TODO(\"Not yet implemented\")")
        }.asString())

    /**
     * For the purpose of creating Composable functions, optimistically guesses
     * that [expression] is of type `Unit`.
     */
    private fun KtAnalysisSession.guessReturnType(expression: KtExpression): KtType {
      return (expression.getKtType() as? KtFunctionalType)?.returnType ?: builtinTypes.UNIT
    }
  }
}
