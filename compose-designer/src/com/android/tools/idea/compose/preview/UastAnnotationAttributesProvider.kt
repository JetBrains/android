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
package com.android.tools.idea.compose.preview

import com.android.tools.preview.AnnotationAttributesProvider
import com.intellij.psi.PsiLiteralExpression
import com.intellij.util.text.nullize
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression

/** [AnnotationAttributesProvider] implementation based on [UAnnotation]. */
internal class UastAnnotationAttributesProvider(
  private val annotation: UAnnotation,
  private val defaultValues: Map<String, String?>,
) : AnnotationAttributesProvider {

  override fun <T> getAttributeValue(attributeName: String): T? {
    val expression = annotation.findAttributeValue(attributeName)
    return expression?.getValueOfType()
  }

  override fun getIntAttribute(attributeName: String): Int? {
    return getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toInt()
  }

  override fun getStringAttribute(attributeName: String): String? {
    return getAttributeValue<String>(attributeName)?.nullize() ?: defaultValues[attributeName]
  }

  override fun getFloatAttribute(attributeName: String): Float? {
    return getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toFloat()
  }

  override fun getBooleanAttribute(attributeName: String): Boolean? {
    return getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toBoolean()
  }

  override fun <T> getDeclaredAttributeValue(attributeName: String): T? {
    val expression = annotation.findDeclaredAttributeValue(attributeName)
    return expression?.getValueOfType() as T?
  }

  override fun findClassNameValue(name: String): String? =
    (annotation.findAttributeValue(name) as? UClassLiteralExpression)?.type?.canonicalText
}

private inline fun <T> UExpression.getValueOfType(): T? {
  val value = this.evaluate() as? T
  // Cast to literal as fallback. Needed for example for MultiPreview imported from a binary file
  return value ?: (this.sourcePsi as? PsiLiteralExpression)?.value as? T
}
