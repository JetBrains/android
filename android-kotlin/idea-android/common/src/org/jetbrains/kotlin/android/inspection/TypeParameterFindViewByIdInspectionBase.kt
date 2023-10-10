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

package org.jetbrains.kotlin.android.inspection

import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtPsiUtil.isUnsafeCast
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.addTypeArgument
import org.jetbrains.kotlin.psi.psiUtil.getBinaryWithTypeParent

abstract class TypeParameterFindViewByIdInspectionBase : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        val compileSdk = AndroidFacet.getInstance(session.file)
                ?.let { facet -> StudioAndroidModuleInfo.getInstance(facet) }
                ?.buildSdkVersion
                ?.apiLevel

        if (compileSdk == null || compileSdk < 26) {
            return KtVisitorVoid()
        }

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName !in APPLICABLE_FUNCTION_NAMES || expression.typeArguments.isNotEmpty()) {
                    return
                }

                val parentCast = expression.parentCast()?.takeIf { isUnsafeCast(it) } ?: return
                val typeText = parentCast.right?.getTypeTextWithoutQuestionMark() ?: return
                if (!expression.isValidFindViewByIdCallForCast(parentCast)) return

                val resultText = "$calleeName<$typeText>(...)"

                holder.registerProblem(
                        parentCast,
                        "Can be converted to $resultText",
                        ConvertCastToFindViewByIdWithTypeParameter(resultText)
                )
            }
        }
    }

    abstract fun KtCallExpression.isValidFindViewByIdCallForCast(cast: KtBinaryExpressionWithTypeRHS): Boolean

    class ConvertCastToFindViewByIdWithTypeParameter(private val resultText: String) : LocalQuickFix {
        override fun getFamilyName(): String = "Convert cast to type parameter"

        override fun getName(): String = "Convert cast to $resultText"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val cast = descriptor.psiElement as? KtBinaryExpressionWithTypeRHS ?: return
            val typeText = cast.right?.getTypeTextWithoutQuestionMark() ?: return
            cast.childCall() ?: return

            // Clone the entire cast expression...
            val newCast = cast.copy() as KtBinaryExpressionWithTypeRHS
            // ...find the function call...
            val newCall = newCast.childCall() ?: return
            // ...and add a type parameter to the call.
            val typeArgument = KtPsiFactory(project, markGenerated = true).createTypeArgument(typeText)
            newCall.addTypeArgument(typeArgument)

            // Replace the entire cast expression with the cast's LHS. This discards the `as` keyword and typename,
            // leaving only the (potentially dot-qualified) call to findViewById and friends.
            cast.replace(KtPsiUtil.safeDeparenthesize(newCast.left))
        }

    }

    companion object {
        fun KtTypeReference.getTypeTextWithoutQuestionMark(): String? =
                (typeElement as? KtNullableType)?.innerType?.text ?: typeElement?.text

        val APPLICABLE_FUNCTION_NAMES = setOf("findViewById", "findViewWithTag", "requireViewById")

        // The PSI can have the following shapes:
        // (a) Bare findViewById (findViewById(...) as Foo):
        //   - KtBinaryExpressionWithTypeRHS(... as ...)
        //     - left: KtCallExpression(findViewById(...))
        //     - right: KtTypeReference(Foo)
        // (b) Qualified findViewById(someExpression().findViewById(...) as Foo):
        //     (Note: This can be dot-qualified (expr.findViewById), or safe-qualified (expr?.findViewById).
        //   - KtBinaryExpressionWithTypeRHS(... as ...)
        //     - left: Kt(Dot|Safe)QualifiedExpression(someExpression().findViewById(...))
        //       - receiver: KtExpression(someExpression())
        //       - selector: KtCallExpression(findViewById(...))
        //     - right: KtTypeReference(Foo)
        //
        // The parentCast and childCall functions navigate to the KtBinaryExpressionWithTypeRHS (as)
        // from the KtCallExpression (findViewById), and vice-versa.
        //
        // Note: The receiver expression may also be labeled, parenthesized, or annotated - these cases
        // are handled by KtPsiUtil.deparenthesize(), which is also called by getBinaryWithTypeParent().

        private fun KtCallExpression.parentCast(): KtBinaryExpressionWithTypeRHS? =
            calleeExpression?.getBinaryWithTypeParent()

        private fun KtBinaryExpressionWithTypeRHS.childCall(): KtCallExpression? =
            when (val left = KtPsiUtil.deparenthesize(left)) {
                is KtCallExpression -> left
                is KtQualifiedExpression -> left.selectorExpression as? KtCallExpression
                else -> null
            }
    }
}