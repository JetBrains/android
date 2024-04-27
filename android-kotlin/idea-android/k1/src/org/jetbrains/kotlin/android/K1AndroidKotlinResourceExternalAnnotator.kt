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
package org.jetbrains.kotlin.android

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class K1AndroidKotlinResourceExternalAnnotator : AndroidKotlinResourceExternalAnnotatorBase() {
    override fun KtNameReferenceExpression.resolveToResourceReference(): ResourceReference? {
        val referenceTarget = resolveToCall()?.resultingDescriptor as? JavaPropertyDescriptor ?: return null
        val type = referenceTarget.getAndroidResourceType() ?: return null
        if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
            val referenceType = referenceTarget.getResourceReferenceType()
            return ResourceReference(referenceType.namespace, type, getReferencedName())
        }
        return null
    }

    companion object {
        private fun JavaPropertyDescriptor.getAndroidResourceType(): ResourceType? {
            if (getResourceReferenceType() == AndroidPsiUtils.ResourceReferenceType.NONE) {
                return null
            }

            val containingClass = containingDeclaration as? JavaClassDescriptor ?: return null
            return ResourceType.fromClassName(containingClass.name.asString())
        }

        private fun JavaPropertyDescriptor.getResourceReferenceType(): AndroidPsiUtils.ResourceReferenceType {
            val containingClass = containingDeclaration as? JavaClassDescriptor ?: return AndroidPsiUtils.ResourceReferenceType.NONE
            val rClass = containingClass.containingDeclaration as? JavaClassDescriptor ?: return AndroidPsiUtils.ResourceReferenceType.NONE

            if (SdkConstants.R_CLASS == rClass.name.asString()) {
                return if ((rClass.containingDeclaration as? PackageFragmentDescriptor)?.fqName?.asString() == SdkConstants.ANDROID_PKG) {
                    AndroidPsiUtils.ResourceReferenceType.FRAMEWORK
                }
                else {
                    AndroidPsiUtils.ResourceReferenceType.APP
                }
            }

            return AndroidPsiUtils.ResourceReferenceType.NONE
        }
    }

}