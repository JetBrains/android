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
import com.intellij.util.text.nullize
import org.jetbrains.uast.UAnnotation

/** [AnnotationAttributesProvider] implementation based on [UAnnotation]. */
internal class UastAnnotationAttributesProvider(
  private val annotation: UAnnotation,
  private val defaultValues: Map<String, String?>
) : AnnotationAttributesProvider {
  override fun getIntAttribute(attributeName: String): Int? {
    return annotation.getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toInt()
  }

  override fun getStringAttribute(attributeName: String): String? {
    return annotation.getAttributeValue<String>(attributeName)?.nullize()
      ?: defaultValues[attributeName]
  }

  override fun getFloatAttribute(attributeName: String): Float? {
    return annotation.getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toFloat()
  }

  override fun getBooleanAttribute(attributeName: String): Boolean? {
    return annotation.getAttributeValue(attributeName) ?: defaultValues[attributeName]?.toBoolean()
  }
}
