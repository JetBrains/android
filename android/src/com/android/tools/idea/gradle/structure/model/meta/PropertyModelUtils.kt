/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel

fun ResolvedPropertyModel.asString(): String? = when (valueType) {
  ValueType.STRING -> getValue(GradlePropertyModel.STRING_TYPE)
  // Implicitly convert Integer values to String. Dual String/Integer properties are common
  // and the only risk is accidental replacement of an Integer constant with the equivalent
  // String constant where both are acceptable.
  ValueType.INTEGER -> getValue(GradlePropertyModel.INTEGER_TYPE)?.toString()
  else -> null
}

fun ResolvedPropertyModel.asInt(): Int? = when (valueType) {
  ValueType.INTEGER -> getValue(GradlePropertyModel.INTEGER_TYPE)
  else -> null
}

fun ResolvedPropertyModel.asBoolean(): Boolean? = when (valueType) {
  ValueType.BOOLEAN -> getValue(GradlePropertyModel.BOOLEAN_TYPE)
  else -> null
}

fun ResolvedPropertyModel.dslText(): String? = getRawValue(GradlePropertyModel.STRING_TYPE)
fun ResolvedPropertyModel.clear() = unresolvedModel.delete()
