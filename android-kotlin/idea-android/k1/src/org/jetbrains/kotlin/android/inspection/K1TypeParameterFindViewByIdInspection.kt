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

import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.types.isNullabilityFlexible

class K1TypeParameterFindViewByIdInspection : TypeParameterFindViewByIdInspectionBase() {
    override fun KtCallExpression.classifyFindViewCall(cast: KtBinaryExpressionWithTypeRHS): FindViewCallInfo? {
        val callableDescriptor = resolveToCall()?.resultingDescriptor ?: return null
        if (callableDescriptor.name.asString() !in APPLICABLE_FUNCTION_NAMES) return null
        val returnType = callableDescriptor.returnType ?: return null

        callableDescriptor.typeParameters.singleOrNull() ?: return null
        // The original K1-only version of this inspection did not type-check the type parameter against
        // the type of the cast, so we don't do so here either.
        return FindViewCallInfo(
            returnTypeNullability = when {
                returnType.isNullabilityFlexible() -> ReturnTypeNullability.PLATFORM_TYPE
                returnType.isMarkedNullable -> ReturnTypeNullability.NULLABLE
                else -> ReturnTypeNullability.NOT_NULL
            }
        )
    }
}