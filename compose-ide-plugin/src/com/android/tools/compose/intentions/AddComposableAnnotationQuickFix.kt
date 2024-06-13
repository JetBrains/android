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
package com.android.tools.compose.intentions

import androidx.compose.compiler.plugins.kotlin.ComposeClassIds
import androidx.compose.compiler.plugins.kotlin.k1.ComposeErrors
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.expectedComposableAnnotationHolder
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Quick fix addressing @Composable function invocations from non-@Composable scopes.
 *
 * As an example:
 * ```kotlin
 * @Composable
 * fun ComposableFunction() {}
 *
 * fun NonComposableFunction() {
 *     ComposableFunction()
 * }
 * ```
 *
 * The call to `ComposableFunction()` within `NonComposableFunction` is not allowed. Both the
 * invocation `ComposableFunction()` and the function declaration `NonComposableFunction` will have
 * an error.
 *
 * This quick fix appears on both errors, and offers to add `@Composable` to
 * `NonComposableFunction`.
 */
class AddComposableAnnotationQuickFix
private constructor(element: KtModifierListOwner, private val displayText: String) :
  KotlinQuickFixAction<KtModifierListOwner>(element) {

  override fun getFamilyName(): String = ComposeBundle.message("add.composable.annotation")

  override fun getText(): String = displayText

  override fun invoke(project: Project, editor: Editor?, file: KtFile) {
    element?.addAnnotation(ComposeClassIds.Composable)

    // TODO(311812857): `addAnnotation()` internally calls the reference shortener, but the target
    //                  element to shorten seems to be wrong. It will be fixed in the upstream.
    //                  After fixing it, remove the following reference shortener call.
    if (KotlinPluginModeProvider.isK2Mode()) {
      (element?.parent as? KtElement)?.let { parent -> shortenReferences(parent) }
    }
  }

  /**
   * Creates a fix for the COMPOSABLE_INVOCATION error, which appears on a Composable function call
   * from within a non-Composable scope.
   */
  object ComposableInvocationFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
      createAction(diagnostic.psiElement)

    fun createAction(psiElement: PsiElement): AddComposableAnnotationQuickFix? {
      val node = (psiElement as? KtElement)?.expectedComposableAnnotationHolder()
      return node?.takeIf(PsiElement::isWritable)?.toDisplayText()?.let {
        AddComposableAnnotationQuickFix(node, it)
      }
    }
  }

  /**
   * Creates a fix for the COMPOSABLE_EXPECTED error, which appears on a non-Composable scope that
   * contains a Composable function call.
   */
  object ComposableExpectedFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
      createAction(diagnostic.psiElement.parent)

    fun createAction(psiElement: PsiElement): AddComposableAnnotationQuickFix? {
      val node: KtModifierListOwner? =
        when (psiElement) {
          // If there is only one accessor, and it is a getter, then we can figure out what to
          // fix.
          is KtProperty ->
            psiElement.accessors.singleOrNull()?.takeIf(KtPropertyAccessor::isGetter)
              ?: (psiElement.initializer as? KtNamedFunction)
          is KtNamedFunction -> psiElement
          // These are currently the only cases we handle.
          else -> {
            thisLogger()
              .warn("Saw COMPOSABLE_EXPECTED on unhandled element type: ${psiElement.javaClass}")
            null
          }
        }
      return node?.toDisplayText()?.let { AddComposableAnnotationQuickFix(node, it) }
    }
  }

  companion object {
    val k2DiagnosticFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
      val psiElement = diagnostic.psi
      listOfNotNull(
        when (diagnostic.factoryName) {
          "COMPOSABLE_INVOCATION" -> ComposableInvocationFactory.createAction(psiElement)
          "COMPOSABLE_EXPECTED" -> ComposableExpectedFactory.createAction(psiElement)
          else -> null
        }
      )
    }

    private fun KtModifierListOwner.toDisplayText(): String? =
      when (this) {
        is KtTypeReference -> toDisplayText()
        is KtNamedFunction ->
          name?.let { ComposeBundle.message("add.composable.to.element.with.name", name) }
            ?: ComposeBundle.message("add.composable.to.anonymous.function")
        is KtFunctionLiteral -> ComposeBundle.message("add.composable.to.enclosing.lambda")
        is KtPropertyAccessor ->
          takeIf(KtPropertyAccessor::isGetter)?.property?.name?.let {
            ComposeBundle.message("add.composable.to.element.with.name", "$it.get()")
          }
        else -> name?.let { ComposeBundle.message("add.composable.to.element.with.name", it) }
      }

    private fun KtTypeReference.toDisplayText(): String? {
      // First case - this is a type for a function's lambda parameter.
      val param = parent as? KtParameter
      if (param != null) {
        // Traversing parents twice: KtParameter -> KtParameterList -> KtNamedFunction
        val functionName = (param.parent?.parent as? KtNamedFunction)?.name
        val paramName = param.name ?: return null
        if (functionName != null) {
          return ComposeBundle.message(
            "add.composable.to.lambda.parameter",
            functionName,
            paramName,
          )
        }
        return ComposeBundle.message(
          "add.composable.to.lambda.parameter.of.anonymous.function",
          paramName,
        )
      }
      // Second case - this is a type of a property (with a functional type).
      val propertyName = (parent as? KtProperty)?.name ?: return null
      return ComposeBundle.message("add.composable.to.property.type", propertyName)
    }
  }

  class Contributor : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
      // COMPOSABLE_INVOCATION: error goes on the Composable call in a non-Composable function
      quickFixes.register(ComposeErrors.COMPOSABLE_INVOCATION, ComposableInvocationFactory)

      // COMPOSABLE_EXPECTED: error goes on the non-Composable function with Composable calls
      quickFixes.register(ComposeErrors.COMPOSABLE_EXPECTED, ComposableExpectedFactory)
    }
  }
}
