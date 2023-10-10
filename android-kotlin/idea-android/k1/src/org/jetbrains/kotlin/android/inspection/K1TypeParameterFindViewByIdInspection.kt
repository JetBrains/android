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
import org.jetbrains.kotlin.psi.KtTypeReference

class K1TypeParameterFindViewByIdInspection : TypeParameterFindViewByIdInspectionBase() {
    override fun KtCallExpression.isValidFindViewByIdCallForCast(cast: KtBinaryExpressionWithTypeRHS): Boolean {
        val callableDescriptor = resolveToCall()?.resultingDescriptor ?: return false
        if (callableDescriptor.name.asString() !in APPLICABLE_FUNCTION_NAMES) return false

        callableDescriptor.typeParameters.singleOrNull() ?: return false
        // The original K1-only version of this inspection did not type-check the type parameter against
        // the type of the cast, so we don't do so here either.
        return true
    }
}