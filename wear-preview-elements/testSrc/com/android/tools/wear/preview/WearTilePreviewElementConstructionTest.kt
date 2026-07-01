/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.wear.preview

import com.android.tools.preview.AnnotatedMethod
import com.android.tools.preview.AnnotationAttributesProvider
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WearTilePreviewElementConstructionTest {
  @Test
  fun testPreviewAnnotationToWearTilePreviewElement_htmlIsNeutered() {
    val annotatedMethod =
      object : AnnotatedMethod<Unit> {
        override val name = "Method"
        override val qualifiedName = "com.test.Method"
        override val methodBody = null
        override val parameterAnnotations = emptyList<Pair<String, AnnotationAttributesProvider>>()
      }

    val previewElement =
      previewAnnotationToWearTilePreviewElement(
        object : AnnotationAttributesProvider {
          override fun <T> getAttributeValue(attributeName: String): T? = null

          override fun getIntAttribute(attributeName: String): Int? = null

          override fun getStringAttribute(attributeName: String): String? = null

          override fun getFloatAttribute(attributeName: String): Float? = null

          override fun getBooleanAttribute(attributeName: String): Boolean? = null

          @Suppress("UNCHECKED_CAST")
          override fun <T> getDeclaredAttributeValue(attributeName: String): T? =
            when (attributeName) {
              PARAMETER_NAME -> "<html><img src='http://127.0.0.1:9999/aswb046'>" as T
              PARAMETER_GROUP -> "<html>Group" as T
              else -> null
            }

          override fun findClassNameValue(name: String): String? = null
        },
        annotatedMethod,
        null,
        buildPreviewName = { "$it" },
        buildParameterName = { it },
      )

    assertNotNull(previewElement)
    assertEquals("\u200B<html><img src='http://127.0.0.1:9999/aswb046'>", previewElement.displaySettings.name)
    assertEquals("\u200B<html><img src='http://127.0.0.1:9999/aswb046'>", previewElement.displaySettings.parameterName)
    assertEquals("\u200B<html>Group", previewElement.displaySettings.group)
  }
}
