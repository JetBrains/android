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
import com.android.tools.idea.AndroidPsiUtils.ResourceReferenceType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtJavaFieldSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class K2AndroidKotlinResourceExternalAnnotator : AndroidKotlinResourceExternalAnnotatorBase() {
    override fun KtNameReferenceExpression.resolveToResourceReference(): ResourceReference? = analyze(this) {
        val javaFieldSymbol = mainReference.resolveToSymbol() as? KtJavaFieldSymbol ?: return null
        val type = getAndroidResourceType(javaFieldSymbol) ?: return null
        return if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
            val referenceType = getResourceReferenceType(javaFieldSymbol)
            ResourceReference(referenceType.namespace, type, getReferencedName())
        }
        else {
            null
        }
    }

    companion object {
        /**
         * Since this function uses [KtJavaFieldSymbol], it must run inside [analyze].
         */
        private fun KaSession.getAndroidResourceType(field: KtJavaFieldSymbol): ResourceType? {
            if (getResourceReferenceType(field) == ResourceReferenceType.NONE) {
                return null
            }

            val containingClassName = field.callableId?.classId?.shortClassName?.asString() ?: return null
            return ResourceType.fromClassName(containingClassName)
        }

        /**
         * Since this function uses [KtJavaFieldSymbol], it must run inside [analyze].
         */
        private fun KaSession.getResourceReferenceType(field: KtJavaFieldSymbol): ResourceReferenceType {
            val containingClassId = field.callableId?.classId ?: return ResourceReferenceType.NONE
            val rClassName = containingClassId.parentClassId?.shortClassName ?: return ResourceReferenceType.NONE

            if (SdkConstants.R_CLASS == rClassName.asString()) {
                val rClassPackageFqName = containingClassId.packageFqName
                return if (rClassPackageFqName.asString() == SdkConstants.ANDROID_PKG) {
                    ResourceReferenceType.FRAMEWORK
                }
                else {
                    ResourceReferenceType.APP
                }
            }
            return ResourceReferenceType.NONE
        }
    }
}