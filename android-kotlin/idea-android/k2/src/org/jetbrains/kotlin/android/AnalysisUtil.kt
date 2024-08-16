/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject


fun KaSession.isSubclassOf(subClass: KtClassOrObject, superClassId: ClassId, strict: Boolean = false): Boolean {
    val classSymbol = subClass.symbol as? KaClassLikeSymbol ?: return false
    return isSubclassOf(classSymbol, superClassId, strict)
}

fun KaSession.isSubclassOf(classSymbol: KaClassLikeSymbol, superClassId: ClassId, strict: Boolean = false): Boolean =
    isSubclassOf(buildClassType(classSymbol), superClassId, strict)

fun KaSession.isSubclassOf(classType: KaType, superClassId: ClassId, strict: Boolean = false): Boolean {
    val superClassType = buildClassType(superClassId)
    if (superClassType is KaErrorType) return false
    if (!strict && classType.semanticallyEquals(superClassType)) return true
    return classType.isSubtypeOf(superClassType)
}
