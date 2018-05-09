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
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty

fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any, ContextT> T.listProperty(
  description: String,
  getResolvedValue: ResolvedT.() -> List<ValueT>?,
  itemValueGetter: ResolvedPropertyModel.() -> ValueT?,
  itemValueSetter: ResolvedPropertyModel.(ValueT) -> Unit,
  getParsedProperty: ParsedT.() -> ResolvedPropertyModel,
  parse: (ContextT, String) -> ParsedValue<ValueT>,
  format: (ContextT, ValueT) -> String = { _, value -> value.toString() },
  getKnownValues: ((ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>)? = null
) =
  ModelListPropertyImpl(
    this,
    description,
    getResolvedValue,
    { getParsedProperty().asParsedListValue(itemValueGetter, itemValueSetter) },
    { index -> getParsedProperty().addItem(index, itemValueGetter, itemValueSetter) },
    { index -> getParsedProperty().deleteItem(index) },
    { getParsedProperty().dslText() },
    { getParsedProperty().delete() },
    { getParsedProperty().setDslText(it) },
    { context: ContextT, value -> if (value.isBlank()) ParsedValue.NotSet else parse(context, value.trim()) },
    format,
    { context: ContextT, model -> if (getKnownValues != null) getKnownValues(context, model) else immediateFuture(listOf()) }
  )

class ModelListPropertyImpl<in ContextT, in ModelT, out ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  private val getResolvedValue: ResolvedT.() -> List<ValueT>?,
  private val getParsedCollection: ParsedT.() -> List<ModelPropertyParsedCore<ValueT>>?,
  private val addItem: ParsedT.(Int) -> ModelPropertyCore<ValueT>,
  private val itemDeleter: ParsedT.(Int) -> Unit,
  private val getParsedRawValue: ParsedT.() -> DslText?,
  override val clearParsedValue: ParsedT.() -> Unit,
  override val setParsedRawValue: (ParsedT.(DslText) -> Unit),
  override val parser: (ContextT, String) -> ParsedValue<ValueT>,
  override val formatter: (ContextT, ValueT) -> String,
  override val knownValuesGetter: (ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>
) : ModelCollectionPropertyBase<ContextT, ModelT, ResolvedT, ParsedT, List<ValueT>, ValueT>(), ModelListProperty<ContextT, ModelT, ValueT> {

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<List<ValueT>> = getParsedValue(thisRef)

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<List<ValueT>>) = setParsedValue(thisRef, value)

  private fun getEditableValues(model: ModelT): List<ModelPropertyCore<ValueT>> =
    modelDescriptor
      .getParsed(model)
      ?.getParsedCollection()
      // TODO(b/72814329): Replace [null] with the matched value.
      ?.map { makePropertyCore(it, { null }) }
      ?.map { it.makeSetModifiedAware(model) }
        ?: listOf()

  private fun addItem(model: ModelT, index: Int): ModelPropertyCore<ValueT> =
    modelDescriptor.getParsed(model)?.addItem(index)?.makeSetModifiedAware(model).also { model.setModified() }
        ?: throw IllegalStateException()

  private fun deleteItem(model: ModelT, index: Int) =
    modelDescriptor.getParsed(model)?.itemDeleter(index).also { model.setModified() } ?: throw IllegalStateException()

  private fun getParsedValue(model: ModelT): ParsedValue<List<ValueT>> {
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: List<ModelPropertyParsedCore<ValueT>>? = parsedModel?.getParsedCollection()
    val parsed = parsedGradleValue?.mapNotNull { (it.getParsedValue() as? ParsedValue.Set.Parsed<ValueT>)?.value }
    val dslText: DslText? = parsedModel?.getParsedRawValue()
    return makeParsedValue(parsed, dslText)
  }

  private fun getResolvedValue(model: ModelT): ResolvedValue<List<ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: List<ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun bind(model: ModelT): ModelListPropertyCore<ValueT> = object:ModelListPropertyCore<ValueT> {
    override fun getParsedValue(): ParsedValue<List<ValueT>> = this@ModelListPropertyImpl.getParsedValue(model)
    override fun setParsedValue(value: ParsedValue<List<ValueT>>) = this@ModelListPropertyImpl.setParsedValue(model, value)
    override fun getResolvedValue(): ResolvedValue<List<ValueT>> = this@ModelListPropertyImpl.getResolvedValue(model)
    override fun getEditableValues(): List<ModelPropertyCore<ValueT>> = this@ModelListPropertyImpl.getEditableValues(model)
    override fun addItem(index: Int): ModelPropertyCore<ValueT> = this@ModelListPropertyImpl.addItem(model, index)
    override fun deleteItem(index: Int) = this@ModelListPropertyImpl.deleteItem(model, index)
    override val defaultValueGetter: (() -> List<ValueT>?)? = null
  }
}

fun <T : Any> ResolvedPropertyModel?.asParsedListValue(
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): List<ModelPropertyCore<T>>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.LIST }
    ?.getValue(LIST_TYPE)
    ?.map { it.resolve() }
    ?.map { makeItemProperty(it, getTypedValue, setTypedValue) }

fun <T : Any> ResolvedPropertyModel.addItem(
  index: Int,
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): ModelPropertyCore<T> =
  makeItemProperty(addListValueAt(index).resolve(), getTypedValue, setTypedValue)

fun ResolvedPropertyModel.deleteItem(index: Int) = getValue(LIST_TYPE)?.get(index)?.delete() ?: throw IllegalStateException()

