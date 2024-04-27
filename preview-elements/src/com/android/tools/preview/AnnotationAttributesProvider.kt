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
package com.android.tools.preview

/** Provides a unified interface to access annotation attributes (parameters). */
interface AnnotationAttributesProvider {

  fun <T> getAttributeValue(attributeName: String): T?

  fun getIntAttribute(attributeName: String): Int?

  fun getStringAttribute(attributeName: String): String?

  fun getFloatAttribute(attributeName: String): Float?

  fun getBooleanAttribute(attributeName: String): Boolean?

  fun <T> getDeclaredAttributeValue(attributeName: String): T?

  fun findClassNameValue(name: String): String?
}