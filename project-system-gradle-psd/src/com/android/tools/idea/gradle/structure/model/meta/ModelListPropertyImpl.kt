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
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty

fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any> T.listProperty(
  description: String,
  resolvedValueGetter: ResolvedT.() -> List<ValueT>?,
  getter: ResolvedPropertyModel.() -> ValueT?,
  setter: ResolvedPropertyModel.(ValueT) -> Unit,
  parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  parser: (String) -> Annotated<ParsedValue<ValueT>>,
  formatter: (ValueT) -> String = { it.toString() },
  variableMatchingStrategy: VariableMatchingStrategy = VariableMatchingStrategy.BY_TYPE,
  knownValuesGetter: ((ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>)? = null,
  matcher: (model: ModelT, parsedValue: ValueT?, resolvedValue: ValueT) -> Boolean =
    { _, parsedValue, resolvedValue -> parsedValue == resolvedValue }
) =
  ModelListPropertyImpl(
    this,
    description,
    resolvedValueGetter,
    parsedPropertyGetter,
    getter,
    setter,
    { value -> if (value.isBlank()) ParsedValue.NotSet.annotated() else parser(value.trim()) },
    formatter,
    variableMatchingStrategy,
    { model -> if (knownValuesGetter != null) knownValuesGetter(model) else immediateFuture(listOf()) },
    matcher
  )

class ModelListPropertyImpl<ModelT, out ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  private val getResolvedValue: ResolvedT.() -> List<ValueT>?,
  override val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  override val getter: ResolvedPropertyModel.() -> ValueT?,
  override val setter: ResolvedPropertyModel.(ValueT) -> Unit,
  override val parser: (String) -> Annotated<ParsedValue<ValueT>>,
  override val formatter: (ValueT) -> String,
  override val variableMatchingStrategy: VariableMatchingStrategy,
  override val knownValuesGetter: (ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>,
  private val matcher: (model: ModelT, parsed: ValueT?, resolved: ValueT) -> Boolean
) : ModelCollectionPropertyBase<ModelT, ResolvedT, ParsedT, List<ValueT>, ValueT>(), ModelListProperty<ModelT, ValueT> {

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<List<ValueT>> = getParsedValue(thisRef).value

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<List<ValueT>>) = setParsedValue(thisRef, value)

  private fun getEditableValues(model: ModelT): List<ModelPropertyCore<ValueT>> =
    model
      .getParsedProperty()
      ?.asParsedListValue(
        getter, setter, { parsed, resolved -> matcher(model, parsed, resolved) }, { model.modify { it() } })
    // TODO(b/72814329): Replace [null] with the matched value.
    ?: listOf()

  private fun addItem(model: ModelT, index: Int): ModelPropertyCore<ValueT> =
    model.modify {
      getParsedProperty()
        ?.addListItem(
          index,
          getter,
          setter,
          { parsed, resolved -> matcher(model, parsed, resolved) },
          { modify { it() } })
    }
    ?: throw IllegalStateException()

  private fun deleteItem(model: ModelT, index: Int) =
    model.modify {
      getParsedProperty()
      ?.deleteListItem(index)
    }
    ?: throw IllegalStateException()

  private fun getParsedValue(model: ModelT): Annotated<ParsedValue<List<ValueT>>> {
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: List<ResolvedPropertyModel>? = parsedModel?.parsedPropertyGetter().asResolvedPropertiesList()
    val parsed = parsedGradleValue?.mapNotNull { it.getter() }
    val dslText: Annotated<DslText>? = parsedModel?.parsedPropertyGetter()?.dslText(effectiveValueIsNull = parsed == null)
    return makeAnnotatedParsedValue(parsed, dslText)
  }

  private fun getResolvedValue(model: ModelT): ResolvedValue<List<ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: List<ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun bind(model: ModelT): ModelListPropertyCore<ValueT> = object: ModelListPropertyCore<ValueT> {
    override val description: String get() = this@ModelListPropertyImpl.description
    override fun getParsedValue(): Annotated<ParsedValue<List<ValueT>>> = this@ModelListPropertyImpl.getParsedValue(model)
    override fun setParsedValue(value: ParsedValue<List<ValueT>>) = this@ModelListPropertyImpl.setParsedValue(model, value)
    override fun getResolvedValue(): ResolvedValue<List<ValueT>> = this@ModelListPropertyImpl.getResolvedValue(model)
    override fun getEditableValues(): List<ModelPropertyCore<ValueT>> = this@ModelListPropertyImpl.getEditableValues(model)
    override fun addItem(index: Int): ModelPropertyCore<ValueT> = this@ModelListPropertyImpl.addItem(model, index)
    override fun deleteItem(index: Int) = this@ModelListPropertyImpl.deleteItem(model, index)
    override val defaultValueGetter: (() -> List<ValueT>?)? = null
    override val variableScope: (() -> PsVariablesScope?)? = null
    override val isModified: Boolean? get() = model.getParsedProperty()?.isModified

    override fun annotateParsedResolvedMismatch(): ValueAnnotation? = annotateParsedResolvedMismatchBy { parsedValue, resolvedValue ->
      if (parsedValue?.size != resolvedValue.size) false
      else parsedValue.zip(resolvedValue).all { (parsedValue, resolvedValue) -> matcher(model, parsedValue, resolvedValue) }
    }
  }
}

private fun ResolvedPropertyModel?.asResolvedPropertiesList(): List<ResolvedPropertyModel>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.LIST }
    ?.getValue(LIST_TYPE)
    ?.map { it.resolve() }

private fun <T : Any> ResolvedPropertyModel?.asParsedListValue(
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean,
  modifier: (() -> Unit) -> Unit
): List<ModelPropertyCore<T>>? =
  this
    .asResolvedPropertiesList()
    ?.map { makeItemPropertyCore(it, getter, setter, { ResolvedValue.NotResolved() }, matcher, modifier) }

private fun <T : Any> ResolvedPropertyModel.addListItem(
  index: Int,
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean,
  modifier: (() -> Unit) -> Unit
): ModelPropertyCore<T> =
  makeItemPropertyCore(
    addListValueAt(index)!!.resolve(), getter, setter, { ResolvedValue.NotResolved() }, matcher, modifier)

private fun ResolvedPropertyModel.deleteListItem(index: Int) = getValue(LIST_TYPE)?.get(index)?.delete() ?: throw IllegalStateException()

