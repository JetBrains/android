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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.values.GradleValue
import java.io.File

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

fun ResolvedPropertyModel.asFile(): File? = when (valueType) {
  ValueType.STRING -> File(getValue(GradlePropertyModel.STRING_TYPE))
  else -> null
}

fun ResolvedPropertyModel.clear() = unresolvedModel.delete()
fun ResolvedPropertyModel.dslText(): DslText? {
  val text = getRawValue(GradlePropertyModel.OBJECT_TYPE)?.toString()
  return when {
    text == null && unresolvedModel.valueType == GradlePropertyModel.ValueType.NONE -> null
    else -> {
      DslText(
          mode = when {
            unresolvedModel.valueType == ValueType.REFERENCE && dependencies.isEmpty() -> DslMode.OTHER_UNPARSED_DSL_TEXT
            unresolvedModel.valueType == ValueType.UNKNOWN -> DslMode.OTHER_UNPARSED_DSL_TEXT
            unresolvedModel.valueType == ValueType.REFERENCE -> DslMode.REFERENCE
            dependencies.isEmpty() -> DslMode.LITERAL
            unresolvedModel.valueType == ValueType.STRING -> DslMode.INTERPOLATED_STRING
            else -> throw IllegalStateException("Property value of type ${unresolvedModel.valueType} with dependencies is not supported.")
          },
          text = text)
    }
  }
}

fun <T> GradleValue<T>.dsl(): DslText? =
    dslText?.let { DslText(DslMode.LITERAL, it) }

fun ResolvedPropertyModel.setDslText(value: DslText) = when (value.mode) {
  DslMode.REFERENCE -> unresolvedModel.setValue(ReferenceTo(value.text!!))  // null text is invalid here.
  DslMode.LITERAL -> throw IllegalArgumentException("Literal values should not be set via DslText.")
  DslMode.INTERPOLATED_STRING -> unresolvedModel.setValue(GradlePropertyModel.iStr(value.text!!))  // null text is invalid here.
  DslMode.OTHER_UNPARSED_DSL_TEXT -> TODO("Setting unparsed dsl text is not yet supported.")
}
