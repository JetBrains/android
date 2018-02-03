/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import kotlin.reflect.KProperty

fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any> T.listProperty(
  description: String,
  getResolvedValue: ResolvedT.() -> List<ValueT>?,
  getParsedCollection: ParsedT.() -> List<ModelPropertyCore<Unit, ValueT>>?,
  getParsedRawValue: ParsedT.() -> DslText?,
  clearParsedValue: ParsedT.() -> Unit,
  setParsedRawValue: (ParsedT.(DslText) -> Unit),
  parse: (String) -> ParsedValue<ValueT>,
  getKnownValues: ((ModelT) -> List<ValueDescriptor<ValueT>>)? = null
) =
    ModelListPropertyImpl(
      this,
      description,
      getResolvedValue,
      getParsedCollection,
      getParsedRawValue,
      clearParsedValue,
      setParsedRawValue,
      { if (it.isBlank()) ParsedValue.NotSet() else parse(it.trim()) },
      { if (getKnownValues != null) getKnownValues(it) else null }
    )

class ModelListPropertyImpl<in ModelT, ResolvedT, ParsedT, ValueT : Any>(
  private val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  private val getResolvedValue: ResolvedT.() -> List<ValueT>?,
  private val getParsedCollection: ParsedT.() -> List<ModelPropertyCore<Unit, ValueT>>?,
  private val getParsedRawValue: ParsedT.() -> DslText?,
  private val clearParsedValue: ParsedT.() -> Unit,
  private val setParsedRawValue: (ParsedT.(DslText) -> Unit),
  private val parser: (String) -> ParsedValue<ValueT>,
  private val knownValuesGetter: (ModelT) -> List<ValueDescriptor<ValueT>>?
) : ModelListProperty<ModelT, ValueT> {

  override fun getEditableValues(model: ModelT): List<ModelPropertyCore<Unit, ValueT>> =
    modelDescriptor.getParsed(model)?.let { getParsedCollection(it)?.map { makeSetModifiedAware(it, model) } } ?: listOf()

  private fun makeSetModifiedAware(
    it: ModelPropertyCore<Unit, ValueT>,
    updatedModel: ModelT
  ) =
    object : ModelPropertyCore<Unit, ValueT> by it {
      override fun setValue(model: Unit, value: ParsedValue<ValueT>) {
        it.setValue(value)
        modelDescriptor.setModified(updatedModel)
      }
    }

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<List<ValueT>> = getValue(thisRef).parsedValue

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<List<ValueT>>) = setValue(thisRef, value)

  override fun getValue(model: ModelT): PropertyValue<List<ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: List<ValueT>? = resolvedModel?.getResolvedValue()
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: List<ModelPropertyCore<Unit, ValueT>>? = parsedModel?.getParsedCollection()
    val parsed = parsedGradleValue?.mapNotNull { (it.getValue().parsedValue as? ParsedValue.Set.Parsed<ValueT>)?.value}
    val dslText: DslText? = parsedModel?.getParsedRawValue()
    val parsedValue: ParsedValue<List<ValueT>> = when {
      parsedGradleValue == null || (parsed == null && dslText == null) -> ParsedValue.NotSet()
      parsed == null -> ParsedValue.Set.Invalid(dslText?.text.orEmpty(), "Unknown")
      else -> ParsedValue.Set.Parsed(
        value = parsed,
        dslText = dslText
      )
    }
    val resolvedValue = when (resolvedModel) {
      null -> ResolvedValue.NotResolved<List<ValueT>>()
      else -> ResolvedValue.Set(resolved)
    }
    return PropertyValue(parsedValue, resolvedValue)
  }

  override fun setValue(model: ModelT, value: ParsedValue<List<ValueT>>) {
    val parsedModel = modelDescriptor.getParsed(model) ?: throw IllegalStateException()
    when (value) {
      is ParsedValue.NotSet -> parsedModel.clearParsedValue()
      is ParsedValue.Set.Parsed -> {
        val dsl = value.dslText
        when (dsl?.mode) {
          // Dsl modes.
          DslMode.REFERENCE -> parsedModel.setParsedRawValue(dsl)
          DslMode.INTERPOLATED_STRING -> parsedModel.setParsedRawValue(dsl)
          DslMode.OTHER_UNPARSED_DSL_TEXT -> parsedModel.setParsedRawValue(dsl)
          // Literal modes are not supported. getEditableValues() should be used.
          DslMode.LITERAL -> UnsupportedOperationException()
          null -> UnsupportedOperationException()
        }
      }
      is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
    }
    modelDescriptor.setModified(model)
  }

  override fun getDefaultValue(model: ModelT): List<ValueT>? = listOf()

  override fun parse(value: String): ParsedValue<ValueT> = parser(value)

  override fun getKnownValues(model: ModelT): List<ValueDescriptor<ValueT>>? = knownValuesGetter(model)
}

fun <T : Any> ResolvedPropertyModel?.asParsedListValue(
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): List<ModelPropertyCore<Unit, T>> {
  return if (this == null) {
    listOf()
  } else {
    if (this.valueType == GradlePropertyModel.ValueType.LIST) {
      val value = getValue(LIST_TYPE)
      value
      ?.map {it.resolve()}
      ?.map {
        object : ModelPropertyCore<Unit, T> {
          override fun getValue(model: Unit): PropertyValue<T> {
            val parsed: T? = it.getTypedValue()
            val dslText: DslText? = it.dslText()
            val parsedValue = when {
              (parsed == null && dslText == null) -> ParsedValue.NotSet<T>()
              parsed == null -> ParsedValue.Set.Invalid(dslText?.text.orEmpty(), "Unknown")
              else -> ParsedValue.Set.Parsed(value = parsed, dslText = dslText)
            }
            return PropertyValue(parsedValue, ResolvedValue.NotResolved())
          }

          override fun setValue(model: Unit, value: ParsedValue<T>) {
            when (value) {
              is ParsedValue.NotSet -> it.delete()
              is ParsedValue.Set.Parsed -> {
                val dsl = value.dslText
                when (dsl?.mode) {
                // Dsl modes.
                  DslMode.REFERENCE -> it.setDslText(dsl)
                  DslMode.INTERPOLATED_STRING -> it.setDslText(dsl)
                  DslMode.OTHER_UNPARSED_DSL_TEXT -> it.setDslText(dsl)
                // Literal modes.
                  DslMode.LITERAL -> it.setTypedValue(value.value!!)
                  null -> it.setTypedValue(value.value!!)
                }
              }
              is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
            }
          }
        }
      } ?: listOf()
    } else {
      listOf()
    }
  }
}

