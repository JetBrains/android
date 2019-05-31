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
package org.jetbrains.kotlin.android.inspection

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.highlighter.IdeErrorMessages
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class IncorrectScopeInspection : AbstractKotlinInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return if (!StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.get() ||
               session.file.androidFacet == null ||
               !TestSourcesFilter.isTestSources(session.file.virtualFile, session.file.project)) {
      PsiElementVisitor.EMPTY_VISITOR
    }
    else {
      object : KtVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          if (expression is KtNameReferenceExpression) {
            expression.references.ifNotEmpty {
              val resolveResult = expression.mainReference.resolve() ?: return
              val scope: GlobalSearchScope = expression.resolveScope
              if (!PsiSearchScopeUtil.isInScope(scope, resolveResult)) {
                val diagnostic = Errors.UNRESOLVED_REFERENCE.on(expression, expression)
                val message = IdeErrorMessages.render(diagnostic)
                holder.registerProblem(expression, message, ProblemHighlightType.ERROR)
              }
            }
          }
        }
      }
    }
  }
}