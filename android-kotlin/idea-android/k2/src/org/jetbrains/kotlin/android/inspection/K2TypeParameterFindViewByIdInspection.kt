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

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtCapturedType
import org.jetbrains.kotlin.analysis.api.types.KtDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression

class K2TypeParameterFindViewByIdInspection : TypeParameterFindViewByIdInspectionBase() {
    override fun KtCallExpression.classifyFindViewCall(
        cast: KtBinaryExpressionWithTypeRHS
    ): FindViewCallInfo? = analyze(this) {
        val calleeSymbol = resolveCallOld()?.successfulFunctionCallOrNull()?.symbol as? KaNamedFunctionSymbol ?: return null
        if (calleeSymbol.name.asString() !in APPLICABLE_FUNCTION_NAMES) return null

        // The function must take a single type parameter (T)...
        val typeParameterSymbol = calleeSymbol.typeParameters.singleOrNull() ?: return null
        // ... and return T (or T?).
        val returnType = calleeSymbol.returnType
        if (unwrapToTypeParameterSymbol(returnType) != typeParameterSymbol) return null

        // The target type of the cast must satisfy all of T's bounds.
        // We discard the ? on the cast target type when we execute the quickfix, so we need to check
        // against the non-nullable type here.
        val castTargetType = cast.right?.getKtType() ?: return null
        if (castTargetType is KtErrorType) return null

        val castTargetTypeNonNull = castTargetType.withNullability(KtTypeNullability.NON_NULLABLE)
        if (!typeParameterSymbol.upperBounds.all { castTargetTypeNonNull.isSubTypeOf(it) }) return null

        return FindViewCallInfo(
            returnTypeNullability = when {
                returnType.hasFlexibleNullability -> ReturnTypeNullability.PLATFORM_TYPE
                returnType.isMarkedNullable -> ReturnTypeNullability.NULLABLE
                else -> ReturnTypeNullability.NOT_NULL
            }
        )
    }

    companion object {
        private tailrec fun KaSession.unwrapToTypeParameterSymbol(type: KtType): KaTypeParameterSymbol? =
            when (val expanded = type.fullyExpandedType) {
                is KtTypeParameterType -> expanded.symbol
                is KtFlexibleType -> unwrapToTypeParameterSymbol(expanded.upperBound)
                is KtDefinitelyNotNullType -> unwrapToTypeParameterSymbol(expanded.original)
                is KtCapturedType -> {
                    val projectedType = expanded.projection.type
                    if (projectedType != null) {
                        unwrapToTypeParameterSymbol(projectedType)
                    }
                    else null
                }
                else -> null
            }
    }
}