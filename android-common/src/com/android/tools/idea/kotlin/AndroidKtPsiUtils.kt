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

import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.editor.fixers.range
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


fun KtClass.insideBody(offset: Int): Boolean = getBody()?.range?.contains(offset) ?: false

fun KtProperty.hasBackingField(): Boolean {
  val propertyDescriptor = descriptor as? PropertyDescriptor ?: return false
  return analyze(BodyResolveMode.PARTIAL)[BindingContext.BACKING_FIELD_REQUIRED, propertyDescriptor] ?: false
}

fun List<KtAnnotationEntry>.findAnnotation(annotationName: String) = this.firstOrNull { it.getQualifiedName() == annotationName }

fun KtAnnotationEntry.getQualifiedName(): String? =
  analyze().get(BindingContext.ANNOTATION, this)?.fqName?.asString()

fun KtAnnotationEntry.findArgumentExpression(annotationAttributeName: String): KtExpression? {
  return valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == annotationAttributeName }?.getArgumentExpression()
}

// Original code jetbrains/kotlin/idea/injection/InterpolatedStringInjectorProcessor.kt:98
fun tryEvaluateConstant(expression: KtExpression) =
  ConstantExpressionEvaluator.getConstant(expression, expression.analyze())
    ?.takeUnless { it.isError }
    ?.getValue(TypeUtils.NO_EXPECTED_TYPE)
    ?.safeAs<String>()

