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
package com.android.tools.idea.gradle.dsl.model.catalog

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BIG_DECIMAL
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl
import java.math.BigDecimal

/**
 * Wrap and reference with transformed result type like: libs.versions.version.get().toInteger() that reference to version = "34" Is used
 * for catalog references. Referenced element always has string type so need to transform result according to suffix (ie `toInteger()`)
 */
class CatalogTransformedPropertyModelImpl(realModel: GradlePropertyModelImpl, val realType: GradlePropertyModel.ValueType) :
  ResolvedPropertyModelImpl(realModel) {
  val supportedTypes = listOf(STRING, INTEGER, BIG_DECIMAL, BOOLEAN)

  init {
    require(realType in supportedTypes)
  }

  override fun getValueType(): GradlePropertyModel.ValueType {
    return realType
  }

  override fun setValue(value: Any) {
    resolveModel().setValue(value.toString())
  }

  override fun <T> getValue(typeReference: TypeReference<T>): T? {
    val strValue: String = resolveModel().getValue(STRING_TYPE) ?: return null
    val result: Any =
      when (realType) {
        INTEGER -> strValue.toInt()
        BIG_DECIMAL -> strValue.toBigDecimal()
        BOOLEAN -> strValue.toBoolean()
        STRING -> strValue
        else -> null
      } ?: return null
    return typeReference.castTo(result)
  }

  override fun toInt(): Int? {
    return getValue(INTEGER_TYPE)
  }

  override fun toBigDecimal(): BigDecimal? {
    return getValue(BIG_DECIMAL_TYPE)
  }

  override fun toBoolean(): Boolean? {
    return getValue(BOOLEAN_TYPE)
  }

  override fun toString(): String? {
    return getValue(STRING_TYPE)
  }
}
