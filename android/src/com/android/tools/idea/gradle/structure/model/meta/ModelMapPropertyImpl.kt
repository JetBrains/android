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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import kotlin.reflect.KProperty


fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any> T.mapProperty(
  description: String,
  getResolvedValue: ResolvedT.() -> Map<String, ValueT>?,
  getParsedCollection: ParsedT.() -> Map<String, ModelPropertyCore<Unit, ValueT>>?,
  getParsedRawValue: ParsedT.() -> DslText?,
  clearParsedValue: ParsedT.() -> Unit,
  setParsedRawValue: (ParsedT.(DslText) -> Unit),
  parse: (String) -> ParsedValue<ValueT>,
  getKnownValues: ((ModelT) -> List<ValueDescriptor<ValueT>>)? = null
) =
    ModelMapPropertyImpl(
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

class ModelMapPropertyImpl<in ModelT, ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  val getResolvedValue: ResolvedT.() -> Map<String, ValueT>?,
  private val getParsedCollection: ParsedT.() -> Map<String, ModelPropertyParsedCore<Unit, ValueT>>?,
  private val getParsedRawValue: ParsedT.() -> DslText?,
  override val clearParsedValue: ParsedT.() -> Unit,
  override val setParsedRawValue: (ParsedT.(DslText) -> Unit),
  override val parser: (String) -> ParsedValue<ValueT>,
  override val knownValuesGetter: (ModelT) -> List<ValueDescriptor<ValueT>>?
) : ModelCollectionPropertyBase<ModelT, ResolvedT, ParsedT, Map<String, ValueT>, ValueT>(), ModelMapProperty<ModelT, ValueT> {

  override fun getEditableValues(model: ModelT): Map<String, ModelPropertyCore<Unit, ValueT>> {
    fun getResolvedValue(key: String): ValueT? = modelDescriptor.getResolved(model)?.getResolvedValue()?.get(key)
    return modelDescriptor
      .getParsed(model)
      ?.getParsedCollection()
      ?.mapValues { makeSetModifiedAware(it.value, model) }
      ?.mapValues { makePropertyCore(it.value, resolvedValueGetter = { getResolvedValue(it.key) }) }
        ?: mapOf()
  }

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<Map<String, ValueT>> = getParsedValue(thisRef)

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<Map<String, ValueT>>) = setParsedValue(thisRef, value)

  override fun getParsedValue(model: ModelT): ParsedValue<Map<String, ValueT>> {
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: Map<String, ModelPropertyParsedCore<Unit, ValueT>>? = parsedModel?.getParsedCollection()
    val parsed: Map<String, ValueT>? =
      parsedGradleValue
        ?.mapNotNull {
          (it.value.getParsedValue(Unit) as? ParsedValue.Set.Parsed<ValueT>)?.value?.let { v -> it.key to v }
        }
        ?.toMap()
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

  override fun getResolvedValue(model: ModelT): ResolvedValue<Map<String, ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: Map<String, ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun getDefaultValue(model: ModelT): Map<String, ValueT>? = mapOf()
}

fun <T : Any> ResolvedPropertyModel?.asParsedMapValue(
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): Map<String, ModelPropertyCore<Unit, T>>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.MAP }
    ?.getValue(GradlePropertyModel.MAP_TYPE)
    ?.mapValues { it.value.resolve() }
    ?.mapValues { makeItemProperty(it.value, getTypedValue, setTypedValue) }
