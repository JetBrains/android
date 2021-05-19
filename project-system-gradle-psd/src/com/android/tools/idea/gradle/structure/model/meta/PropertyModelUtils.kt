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
import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil
import com.intellij.pom.java.LanguageLevel
import java.io.File

fun ResolvedPropertyModel.asAny(): Any? = when (valueType) {
  ValueType.STRING -> getValue(GradlePropertyModel.STRING_TYPE)
  ValueType.INTEGER -> getValue(GradlePropertyModel.INTEGER_TYPE)
  ValueType.BIG_DECIMAL -> getValue(GradlePropertyModel.BIG_DECIMAL_TYPE)
  ValueType.BOOLEAN -> getValue(GradlePropertyModel.BOOLEAN_TYPE)
  ValueType.LIST -> getValue(GradlePropertyModel.LIST_TYPE)?.map { it.resolve().getParsedValue { asAny() }.value }
  ValueType.MAP -> getValue(GradlePropertyModel.MAP_TYPE)?.mapValues { it.value.resolve().getParsedValue { asAny() }.value }

  ValueType.REFERENCE,
  ValueType.CUSTOM,
  ValueType.NONE,
  ValueType.UNKNOWN -> null
}

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

fun ResolvedPropertyModel.asLanguageLevel(): LanguageLevel? =
    when (valueType) {
      ValueType.STRING -> LanguageLevelUtil.parseFromGradleString("'{${toString()}'")
      ValueType.BIG_DECIMAL -> LanguageLevelUtil.parseFromGradleString(toString())
      ValueType.REFERENCE -> LanguageLevelUtil.parseFromGradleString(toString())
      else -> null
    }

fun ResolvedPropertyModel.asUnit(): Unit? = when (valueType) {
  ValueType.NONE -> null
  else -> null
}

fun ResolvedPropertyModel.setLanguageLevel(value: LanguageLevel) {
  val exampleString = LanguageLevelUtil.getStringToParse(this)
  setValue(LanguageLevelUtil.convertToGradleString(value, exampleString))
}

fun ResolvedPropertyModel.clear() = unresolvedModel.delete()
fun ResolvedPropertyModel.dslText(effectiveValueIsNull: Boolean): Annotated<DslText>? {
  val text = getRawValue(GradlePropertyModel.OBJECT_TYPE)?.toString()
  return when {
    text == null && unresolvedModel.valueType == GradlePropertyModel.ValueType.NONE -> null
    text == null ->
      throw IllegalStateException(
        "The raw value of property '${unresolvedModel.fullyQualifiedName}' is null while its type is: ${unresolvedModel.valueType}")

    unresolvedModel.valueType == ValueType.REFERENCE && (dependencies.isEmpty() && effectiveValueIsNull) ->
      DslText.Reference(text).annotateWithError("Unresolved reference: $text")

    unresolvedModel.valueType == ValueType.REFERENCE ->
      DslText.Reference(text).annotated()

    unresolvedModel.valueType == ValueType.UNKNOWN || (dependencies.isEmpty() && effectiveValueIsNull) ->
      DslText.OtherUnparsedDslText(text).annotated()

    dependencies.isEmpty() ||
    unresolvedModel.valueType == ValueType.MAP ||
    unresolvedModel.valueType == ValueType.LIST ->
      DslText.Literal.annotated()

    unresolvedModel.valueType == ValueType.STRING ->
      DslText.InterpolatedString(text).annotated()

    else -> throw IllegalStateException(
      "Property value of type ${unresolvedModel.valueType} with dependencies is not supported.")
  }
}

fun ResolvedPropertyModel.setDslText(value: DslText) =
  unresolvedModel.setValue(
    when (value) {
      is DslText.Reference -> ReferenceTo.createReferenceFromText(value.text, unresolvedModel) ?: RawText(value.text, value.text)  // null text is invalid here.
      is DslText.InterpolatedString -> GradlePropertyModel.iStr(value.text)  // null text is invalid here.
      is DslText.OtherUnparsedDslText -> RawText(value.text, value.text)  // null text is invalid here.
      DslText.Literal -> throw IllegalArgumentException("Literal values should not be set via DslText.")
    })

internal fun <T : Any> ResolvedPropertyModel?.getParsedValue(
  getter: ResolvedPropertyModel.() -> T?
): Annotated<ParsedValue<T>> {
  val value = this?.getter()
  return makeAnnotatedParsedValue(value, this?.dslText(effectiveValueIsNull = value == null))
}

internal fun <T : Any> ResolvedPropertyModel.setParsedValue(
  setter: ResolvedPropertyModel.(T) -> Unit,
  nullifier: ResolvedPropertyModel.() -> Unit,
  value: ParsedValue<T>
) {
  when (value) {
    is ParsedValue.NotSet -> {
      nullifier()
    }
    is ParsedValue.Set.Parsed -> {
      val dsl = value.dslText
      when (dsl) {
      // Dsl modes.
        is DslText.Reference -> setDslText(dsl)
        is DslText.InterpolatedString -> setDslText(dsl)
        is DslText.OtherUnparsedDslText -> setDslText(dsl)
      // Literal modes.
        DslText.Literal -> if (value.value != null) {
          setter(value.value)
        }
        else {
          nullifier()
        }
      }
    }
  }
}
