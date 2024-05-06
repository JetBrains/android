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
import com.intellij.util.applyIf
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtPsiUtil.isUnsafeCast
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.addTypeArgument
import org.jetbrains.kotlin.psi.psiUtil.getBinaryWithTypeParent
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis

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
                val castTypeReference = parentCast.right ?: return
                val typeText = castTypeReference.getTypeTextWithoutQuestionMark() ?: return
                val callInfo = expression.classifyFindViewCall(parentCast) ?: return

                val resultText = "$calleeName<$typeText>(...)"

                holder.registerProblem(
                        parentCast,
                        "Can be converted to $resultText",
                        ConvertCastToFindViewByIdWithTypeParameter(resultText, callInfo)
                )
            }
        }
    }

    protected enum class ReturnTypeNullability { NOT_NULL, NULLABLE, PLATFORM_TYPE }

    protected data class FindViewCallInfo(
        val returnTypeNullability: ReturnTypeNullability,
    )

    protected abstract fun KtCallExpression.classifyFindViewCall(cast: KtBinaryExpressionWithTypeRHS): FindViewCallInfo?

    private class ConvertCastToFindViewByIdWithTypeParameter(
        private val resultText: String,
        private val callInfo: FindViewCallInfo,
    ) : LocalQuickFix {
        override fun getFamilyName(): String = "Convert cast to type parameter"

        override fun getName(): String = "Convert cast to $resultText"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val cast = descriptor.psiElement as? KtBinaryExpressionWithTypeRHS ?: return
            val castReference = cast.right ?: return
            val typeText = castReference.getTypeTextWithoutQuestionMark() ?: return
            cast.childCall() ?: return

            val assignmentDeclaration = cast.getOutermostParenthesizerOrThis().parent as? KtProperty
            val psiFactory = KtPsiFactory(project, markGenerated = true)

            // Clone the entire cast expression.
            val newCast = cast.copy() as KtBinaryExpressionWithTypeRHS
            val newCall = newCast.childCall() ?: return

            val nonNullCastNeedsDoubleExcl: Boolean
            if (assignmentDeclaration != null && assignmentDeclaration.matchesTypeReference(castReference)) {
                // The result of findViewById is being assigned to a variable or property with a type
                // that matches the target type of the cast (either explicitly, or by inference).
                // Instead of adding a type parameter, we can add an explicit type to the variable, and
                // allow type inference to handle inferring the type parameter. This has the advantage
                // of handling platform types in a cleaner fashion.

                // First, we can tighten the type bound if the resultant type of the expression is
                // definitely not null. This means that for requireViewById, even if it was previously
                // being assigned to a View?, we can tighten the variable type to View.
                val tightenedResultType = newCast.right!!.typeElement?.let {
                    (it as? KtNullableType)?.innerType?.takeIf {
                        callInfo.returnTypeNullability == ReturnTypeNullability.NOT_NULL
                            // We only do this for vals, because vars may be assigned to null elsewhere.
                            && !assignmentDeclaration.isVar
                            // We also can't do this tightening if findViewById was invoked with a safe-call
                            // (foo?.findViewById(...)), because that expression always has type T?.
                            && newCall.parent !is KtSafeQualifiedExpression
                    } ?: it
                } ?: return

                if (tightenedResultType.text != assignmentDeclaration.typeReference?.typeElement?.text) {
                    assignmentDeclaration.typeReference = psiFactory.createType(tightenedResultType)
                }

                // For a non-null cast target type, we only need a !! if the return type of the findView call
                // is known to be nullable. For platform nullability, the compiler will generate a null-check
                // at the time of assignment, so we don't need to explicitly add one in source.
                nonNullCastNeedsDoubleExcl = callInfo.returnTypeNullability == ReturnTypeNullability.NULLABLE
            } else {
                // Add the type parameter.
                val typeArgument = psiFactory.createTypeArgument(typeText)
                newCall.addTypeArgument(typeArgument)
                // For a non-null cast target type, we need to explicitly use !! if the return type of the
                // findView call isn't known to be non-null, to match the cast behavior. This includes both
                // known nullable types, and platform types that were previously null-checked by the cast.
                nonNullCastNeedsDoubleExcl = callInfo.returnTypeNullability != ReturnTypeNullability.NOT_NULL
            }


            // Replace the entire cast expression with the cast's LHS. This discards the `as` keyword and typename,
            // leaving only the (potentially dot-qualified) call to findViewById and friends.
            val castReplacementExpression =
                KtPsiUtil.safeDeparenthesize(newCast.left)
                    // Add the !! if needed. We need it if our cast was to a non-null type, and the resulting type
                    // of the findView call expression could be null (based on return type as above, or a ?. call).
                    .applyIf(!castReference.isNullable &&
                             (nonNullCastNeedsDoubleExcl || newCall.parent is KtSafeQualifiedExpression)) {
                        psiFactory.createExpressionByPattern("$0!!", this, reformat = true)
                    }

            cast.replace(castReplacementExpression)
        }

        private fun KtProperty.matchesTypeReference(reference: KtTypeReference): Boolean {
            // If the property doesn't have an existing type reference, the type is inferred,
            // and we can add a type to it.
            val propertyTypeReference = typeReference ?: return true
            return propertyTypeReference.getTypeTextWithoutQuestionMark() == reference.getTypeTextWithoutQuestionMark()
        }

    }

    companion object {
        fun KtTypeReference.getTypeTextWithoutQuestionMark(): String? =
                (typeElement as? KtNullableType)?.innerType?.text ?: typeElement?.text

        val KtTypeReference.isNullable: Boolean
            get() = typeElement is KtNullableType

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