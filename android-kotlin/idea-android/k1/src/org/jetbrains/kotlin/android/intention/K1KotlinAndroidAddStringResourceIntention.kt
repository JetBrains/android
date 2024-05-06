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

import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class K1KotlinAndroidAddStringResourceIntention : KotlinAndroidAddStringResourceIntentionBase() {
    override fun KtFunction.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean {
        val descriptor = unsafeResolveToDescriptor() as FunctionDescriptor
        val extendedTypeDescriptor = descriptor.extensionReceiverParameter?.type?.constructor?.declarationDescriptor
        return extendedTypeDescriptor != null && extendedTypeDescriptor.isSubclassOfAny(baseClassIds)
    }

    override fun KtLambdaExpression.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean {
        val bindingContext = analyze(BodyResolveMode.PARTIAL)
        val type = bindingContext.getType(this) ?: return false

        if (!type.isExtensionFunctionType) return false

        val extendedTypeDescriptor = type.arguments.first().type.constructor.declarationDescriptor ?: return false
        return extendedTypeDescriptor.isSubclassOfAny(baseClassIds)
    }

    override fun KtClassOrObject.isSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean =
        resolveToDescriptorIfAny()?.isSubclassOfAny(baseClassIds) ?: false

    companion object {
        private fun ClassifierDescriptor.isSubclassOfAny(classIds: Collection<ClassId>) =
            isSubclassOfAny(classIds.map { it.asSingleFqName() }.toSet())

        private fun ClassifierDescriptor.isSubclassOfAny(fqNames: Set<FqName>): Boolean =
            fqNameSafe in fqNames || isStrictSubclassOfAny(fqNames)

        private fun ClassifierDescriptor.isStrictSubclassOfAny(fqNames: Set<FqName>): Boolean =
            defaultType.constructor.supertypes.any {
                it.constructor.declarationDescriptor?.isSubclassOfAny(fqNames) ?: false
            }
    }
}