/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


/** Checks if the given offset is within [KtClass.getBody] of this [KtClass]. */
fun KtClass.insideBody(offset: Int): Boolean = (body as? PsiElement)?.textRange?.contains(offset) ?: false

/** Checks if this [KtProperty] has a backing field or implements get/set on its own. */
fun KtProperty.hasBackingField(): Boolean {
  val propertyDescriptor = descriptor as? PropertyDescriptor ?: return false
  return analyze(BodyResolveMode.PARTIAL)[BindingContext.BACKING_FIELD_REQUIRED, propertyDescriptor] ?: false
}

/** Computes the qualified name of this [KtAnnotationEntry]. */
fun KtAnnotationEntry.getQualifiedName(): String? {
  return analyze().get(BindingContext.ANNOTATION, this)?.fqName?.asString()
}

/**
 * Finds the [KtExpression] assigned to [annotationAttributeName] in this [KtAnnotationEntry].
 *
 * @see org.jetbrains.kotlin.psi.ValueArgument.getArgumentExpression
 */
fun KtAnnotationEntry.findArgumentExpression(annotationAttributeName: String): KtExpression? {
  return valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == annotationAttributeName }?.getArgumentExpression()
}

/**
 * Tries to evaluate this [KtExpression] as a constant-time constant string.
 *
 * Based on InterpolatedStringInjectorProcessor in the Kotlin plugin.
 */
fun KtExpression.tryEvaluateConstant(): String? {
  return ConstantExpressionEvaluator.getConstant(this, analyze())
    ?.takeUnless { it.isError }
    ?.getValue(TypeUtils.NO_EXPECTED_TYPE)
    ?.safeAs<String>()
}

/**
 * When given an element in a qualified chain expression (eg. activity in R.layout.activity), this finds the previous element in the chain
 * (In this case layout).
 */
fun KtExpression.getPreviousInQualifiedChain(): KtExpression? {
  val receiverExpression = getQualifiedExpressionForSelector()?.receiverExpression
  return (receiverExpression as? KtQualifiedExpression)?.selectorExpression ?: receiverExpression
}

fun KotlinType.getQualifiedName() = constructor.declarationDescriptor?.fqNameSafe

fun KotlinType.isSubclassOf(className: String, strict: Boolean = false): Boolean {
    return (!strict && getQualifiedName()?.asString() == className) || constructor.supertypes.any {
      it.getQualifiedName()?.asString() == className || it.isSubclassOf(className, true)
    }
}
