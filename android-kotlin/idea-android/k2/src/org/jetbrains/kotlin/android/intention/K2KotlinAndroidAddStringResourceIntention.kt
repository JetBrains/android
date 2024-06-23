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
package org.jetbrains.kotlin.android.intention

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.android.isSubclassOf
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression

class K2KotlinAndroidAddStringResourceIntention : KotlinAndroidAddStringResourceIntentionBase() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun KtFunction.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean {
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
            allowAnalysisFromWriteAction {
                analyze(this) {
                    val functionSymbol = symbol as? KaFunctionSymbol ?: return false
                    val receiverType = functionSymbol.receiverParameter?.type ?: return false
                    return baseClassIds.any { isSubclassOf(receiverType, it, strict = false) }
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun KtLambdaExpression.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean {
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
            allowAnalysisFromWriteAction {
                analyze(this) {
                    val type = getKtType() as? KaFunctionType ?: return false
                    val extendedType = type.receiverType ?: return false
                    return baseClassIds.any { isSubclassOf(extendedType, it, strict = false) }
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun KtClassOrObject.isSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean {
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
            allowAnalysisFromWriteAction {
                analyze(this) {
                    val classOrObjectSymbol = getClassOrObjectSymbol() ?: return false
                    return baseClassIds.any { isSubclassOf(classOrObjectSymbol, it, strict = false) }
                }
            }
        }
    }
}