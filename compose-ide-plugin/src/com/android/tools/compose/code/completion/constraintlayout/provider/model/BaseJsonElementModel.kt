/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout.provider.model

import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * Base model for [JsonElement], sets a pointer to avoid holding to the element itself.
 */
internal abstract class BaseJsonElementModel<E: JsonElement>(element: E) {
  protected val elementPointer = SmartPointerManager.createPointer(element)
}

/**
 * Base model for a [JsonProperty].
 *
 * Populates some common fields and provides useful function while avoiding holding to PsiElement instances.
 */
internal abstract class BaseJsonPropertyModel(element: JsonProperty): BaseJsonElementModel<JsonProperty>(element) {
  /**
   * [List] of all the children of this element that are [JsonProperty].
   */
  protected val innerProperties: List<JsonProperty> =
    elementPointer.element?.getChildOfType<JsonObject>()?.propertyList?.toList() ?: emptyList()

  /**
   * Names of all declared properties in this Json.
   */
  val declaredFieldNames: List<String> = innerProperties.map { it.name }

  /**
   * For the children of the current element, returns the [JsonProperty] which name matches the given [name]. Null if none of them does.
   */
  protected fun findProperty(name: String): JsonProperty? =
    innerProperties.firstOrNull { it.name == name }
}