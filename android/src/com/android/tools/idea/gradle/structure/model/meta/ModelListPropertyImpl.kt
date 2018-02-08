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

class ModelListPropertyImpl<in ModelT, out ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  private val getResolvedValue: ResolvedT.() -> List<ValueT>?,
  private val getParsedCollection: ParsedT.() -> List<ModelPropertyParsedCore<Unit, ValueT>>?,
  private val getParsedRawValue: ParsedT.() -> DslText?,
  override val clearParsedValue: ParsedT.() -> Unit,
  override val setParsedRawValue: (ParsedT.(DslText) -> Unit),
  override val parser: (String) -> ParsedValue<ValueT>,
  override val knownValuesGetter: (ModelT) -> List<ValueDescriptor<ValueT>>?
) : ModelCollectionPropertyBase<ModelT, ResolvedT, ParsedT, List<ValueT>, ValueT>(), ModelListProperty<ModelT, ValueT> {

  override fun getEditableValues(model: ModelT): List<ModelPropertyCore<Unit, ValueT>> =
    modelDescriptor
      .getParsed(model)
      ?.getParsedCollection()
      // TODO(b/72814329): Replace [null] with the matched value.
      ?.map { makePropertyCore(it, { null }) }
      ?.map { it.makeSetModifiedAware(model) }
        ?: listOf()

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<List<ValueT>> = getParsedValue(thisRef)

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<List<ValueT>>) = setParsedValue(thisRef, value)

  override fun getParsedValue(model: ModelT): ParsedValue<List<ValueT>> {
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: List<ModelPropertyParsedCore<Unit, ValueT>>? = parsedModel?.getParsedCollection()
    val parsed = parsedGradleValue?.mapNotNull { (it.getParsedValue(Unit) as? ParsedValue.Set.Parsed<ValueT>)?.value }
    val dslText: DslText? = parsedModel?.getParsedRawValue()
    return when {
      parsedGradleValue == null || (parsed == null && dslText == null) -> ParsedValue.NotSet()
      parsed == null -> ParsedValue.Set.Invalid(dslText?.text.orEmpty(), "Unknown")
      else -> ParsedValue.Set.Parsed(
        value = parsed,
        dslText = dslText
      )
    }
  }

  override fun getResolvedValue(model: ModelT): ResolvedValue<List<ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: List<ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun getDefaultValue(model: ModelT): List<ValueT>? = listOf()
}

fun <T : Any> ResolvedPropertyModel?.asParsedListValue(
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): List<ModelPropertyCore<Unit, T>>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.LIST }
    ?.getValue(LIST_TYPE)
    ?.map { it.resolve() }
    ?.map { makeItemProperty(it, getTypedValue, setTypedValue) }
