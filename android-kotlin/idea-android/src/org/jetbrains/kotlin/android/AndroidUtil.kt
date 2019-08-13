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
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

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