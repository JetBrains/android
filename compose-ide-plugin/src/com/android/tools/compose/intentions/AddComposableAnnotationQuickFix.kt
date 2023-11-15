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
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtCompilerPluginDiagnostic0
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTryExpression

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
private constructor(element: KtModifierListOwner, private val displayName: String) :
  KotlinQuickFixAction<KtModifierListOwner>(element) {

  override fun getFamilyName(): String = ComposeBundle.message("add.composable.to.function")

  override fun getText(): String =
    ComposeBundle.message("add.composable.to.function.with.name", displayName)

  override fun invoke(project: Project, editor: Editor?, file: KtFile) {
    element?.addAnnotation(ComposeClassIds.Composable)
  }

  /**
   * Creates a fix for the COMPOSABLE_INVOCATION error, which appears on a non-Composable function
   * that contains a Composable call.
   */
  object ComposableInvocationFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
      createAction(diagnostic.psiElement)

    fun createAction(psiElement: PsiElement): AddComposableAnnotationQuickFix? {
      // Look for the containing function. This logic is based on ComposableCallChecker.check, which
      // walks up the tree and terminates at
      // various places depending on the structure of the code.
      var node: PsiElement? = psiElement as? KtElement
      while (node != null) {
        // Order of when statements (and empty statements) are kept, to mimic the behavior in
        // ComposableCallChecker.check.
        // In cases where we terminate without returning a fix, it indicates the compiler didn't
        // identify a containing function that could
        // be annotated with @Composable to fix the error.
        when (node) {
          is KtFunctionLiteral -> {
            /* keep going */
          }
          is KtLambdaExpression -> {
            // Terminate at containing lambda. There are some scenarios where the compiler may not
            // terminate (if the lambda is inline) and
            // therefore may go higher up to a containing named function, and we won't offer the fix
            // in those scenarios even though it may
            // apply. This is a tactical decision because (1) the full logic is relatively complex
            // to reimplement here, and (2) checking for
            // inline labmdas isn't actually correct (per the TODO in compiler code, it should be
            // looking for CALLS_IN_PLACE instead).
            //
            // This means the quick fix will not fire in some more complicated scenarios where it
            // could actually be applicable; but that's
            // preferable to firing in scenarios where it doesn't actually fix a problem and
            // actually makes things worse. But if there's a
            // containing function that can/should be annotated, it will also have an error which
            // *will* have the quick fix, so it's still
            // available somewhere to the user.
            return null
          }
          is KtTryExpression -> {
            /* keep going */
          }
          is KtFunction -> {
            // Terminate at containing KtFunction.
            if (node !is KtNamedFunction) return null
            val displayName = node.name ?: return null
            return AddComposableAnnotationQuickFix(node, displayName)
          }
          is KtProperty -> return null // Terminate at containing property initializer.
          is KtPropertyAccessor -> {
            // Terminate at containing property accessor.
            if (!node.isGetter) return null
            val displayName = (node.parent as? KtProperty)?.name ?: return null
            return AddComposableAnnotationQuickFix(node, "$displayName.get()")
          }
          is KtCallableReferenceExpression,
          is KtFile,
          is KtClass -> return null
        }
        node = node.parent as? KtElement
      }

      return null
    }
  }

  /**
   * Creates a fix for the COMPOSABLE_EXPECTED error, which appears on a Composable function call
   * from within a non-Composable function.
   */
  object ComposableExpectedFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
      createAction(diagnostic.psiElement.parent)

    fun createAction(psiElement: PsiElement): AddComposableAnnotationQuickFix? {
      val namedFunction = psiElement as? KtNamedFunction ?: return null
      val displayName = namedFunction.name ?: return null

      return AddComposableAnnotationQuickFix(namedFunction, displayName)
    }
  }

  companion object {
    val k2DiagnosticFixFactory: KotlinDiagnosticFixFactory<KtCompilerPluginDiagnostic0> =
      diagnosticFixFactory(KtCompilerPluginDiagnostic0::class) { diagnostic ->
        val psiElement = diagnostic.psi
        listOfNotNull(
          when (diagnostic.factoryName) {
            "COMPOSABLE_INVOCATION" -> ComposableInvocationFactory.createAction(psiElement)
            "COMPOSABLE_EXPECTED" -> ComposableExpectedFactory.createAction(psiElement)
            else -> null
          }
        )
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
