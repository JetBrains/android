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

import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.R_CLASS
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType.APP
import com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType.FRAMEWORK
import com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType.NONE
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject

internal fun JavaPropertyDescriptor.getAndroidResourceType(): ResourceType? {
    if (getResourceReferenceType() == NONE) {
        return null
    }

    val containingClass = containingDeclaration as? JavaClassDescriptor ?: return null
    return ResourceType.fromClassName(containingClass.name.asString())
}

internal fun JavaPropertyDescriptor.getResourceReferenceType(): AndroidPsiUtils.ResourceReferenceType {
    val containingClass = containingDeclaration as? JavaClassDescriptor ?: return NONE
    val rClass = containingClass.containingDeclaration as? JavaClassDescriptor ?: return NONE

    if (R_CLASS == rClass.name.asString()) {
        return if ((rClass.containingDeclaration as? PackageFragmentDescriptor)?.fqName?.asString() == ANDROID_PKG) {
            FRAMEWORK
        }
        else {
            APP
        }
    }

    return NONE
}

internal fun KtAnalysisSession.isSubclassOf(subClass: KtClassOrObject, superClassName: String, strict: Boolean = false): Boolean {
    val classSymbol = subClass.getSymbol() as? KtClassLikeSymbol ?: return false
    val classType = buildClassType(classSymbol) as? KtNonErrorClassType ?: return false

    val superClassType = buildClassType(ClassId.topLevel(FqName(superClassName)))
    if (!strict && classType.isEqualTo(superClassType)) return true
    return classType.isSubTypeOf(superClassType)
}