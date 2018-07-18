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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty


fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any, ContextT> T.mapProperty(
  description: String,
  resolvedValueGetter: ResolvedT.() -> Map<String, ValueT>?,
  getter: ResolvedPropertyModel.() -> ValueT?,
  setter: ResolvedPropertyModel.(ValueT) -> Unit,
  parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  parser: (ContextT, String) -> Annotated<ParsedValue<ValueT>>,
  formatter: (ContextT, ValueT) -> String = { _, value -> value.toString() },
  variableMatchingStrategy: VariableMatchingStrategy = VariableMatchingStrategy.BY_TYPE,
  knownValuesGetter: ((ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>)? = null,
  matcher: (parsedValue: ValueT?, resolvedValue: ValueT) -> Boolean = { parsedValue, resolvedValue -> parsedValue == resolvedValue }
) =
  ModelMapPropertyImpl(
    this,
    description,
    resolvedValueGetter,
    parsedPropertyGetter,
    getter,
    setter,
    { context: ContextT, value -> if (value.isBlank()) ParsedValue.NotSet.annotated() else parser(context, value.trim()) },
    formatter,
    variableMatchingStrategy,
    { context: ContextT, model -> if (knownValuesGetter != null) knownValuesGetter(context, model) else immediateFuture(listOf()) },
    matcher
  )

class ModelMapPropertyImpl<in ContextT, in ModelT, ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  val getResolvedValue: ResolvedT.() -> Map<String, ValueT>?,
  override val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  override val getter: ResolvedPropertyModel.() -> ValueT?,
  override val setter: ResolvedPropertyModel.(ValueT) -> Unit,
  override val parser: (ContextT, String) -> Annotated<ParsedValue<ValueT>>,
  override val formatter: (ContextT, ValueT) -> String,
  override val variableMatchingStrategy: VariableMatchingStrategy,
  override val knownValuesGetter: (ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>,
  private val matcher: (parsedValue: ValueT?, resolvedValue: ValueT) -> Boolean =
    { parsedValue, resolvedValue -> parsedValue == resolvedValue }
) :
  ModelCollectionPropertyBase<ContextT, ModelT, ResolvedT, ParsedT, Map<String, ValueT>, ValueT>(),
  ModelMapProperty<ContextT, ModelT, ValueT> {

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<Map<String, ValueT>> = getParsedValue(thisRef).value

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<Map<String, ValueT>>) = setParsedValue(thisRef, value)

  private fun getEditableValues(model: ModelT): Map<String, ModelPropertyCore<ValueT>> {
    val resolvedValue = modelDescriptor.getResolved(model)?.getResolvedValue()
    return model
      .getParsedProperty()
             ?.asParsedMapValue(getter, setter, matcher, { model.setModified() }, resolvedValue)
           ?: mapOf()
  }

  private fun addEntry(model: ModelT, key: String): ModelPropertyCore<ValueT> =
    model
      .apply { setModified() }
      .getParsedProperty()
      ?.addMapEntry(key, getter, setter, matcher, { model.setModified() })
    ?: throw IllegalStateException()

  private fun deleteEntry(model: ModelT, key: String) =
    model
      .apply { setModified() }
      .getParsedProperty()
      ?.deleteMapEntry(key)
    ?: throw IllegalStateException()

  private fun changeEntryKey(model: ModelT, old: String, new: String): ModelPropertyCore<ValueT> =
  // Both make the property modify-aware and make the model modified since both operations involve changing the model.
    model
      .apply { setModified() }
      .getParsedProperty()
      ?.changeMapEntryKey(old, new, getter, setter, matcher, { model.setModified() })
    ?: throw IllegalStateException()

  private fun getParsedValue(model: ModelT): Annotated<ParsedValue<Map<String, ValueT>>> {
    val parsedProperty = model.getParsedProperty()
    val parsedGradleValue: Map<String, ResolvedPropertyModel>? = parsedProperty.asResolvedPropertiesMap()
    val parsed: Map<String, ValueT>? =
      parsedGradleValue
        ?.mapNotNull {
          it.value.getter()?.let { v -> it.key to v }
        }
        ?.toMap()
    val dslText: Annotated<DslText>? = parsedProperty?.dslText()
    return makeAnnotatedParsedValue(parsed, dslText)
  }

  private fun getResolvedValue(model: ModelT): ResolvedValue<Map<String, ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: Map<String, ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun bind(model: ModelT): ModelMapPropertyCore<ValueT> = object : ModelMapPropertyCore<ValueT> {
    override val description: String = this@ModelMapPropertyImpl.description
    override fun getParsedValue(): Annotated<ParsedValue<Map<String, ValueT>>> = this@ModelMapPropertyImpl.getParsedValue(model)
    override fun setParsedValue(value: ParsedValue<Map<String, ValueT>>) = this@ModelMapPropertyImpl.setParsedValue(model, value)
    override fun getResolvedValue(): ResolvedValue<Map<String, ValueT>> = this@ModelMapPropertyImpl.getResolvedValue(model)
    override fun getEditableValues(): Map<String, ModelPropertyCore<ValueT>> = this@ModelMapPropertyImpl.getEditableValues(model)
    override fun addEntry(key: String): ModelPropertyCore<ValueT> = this@ModelMapPropertyImpl.addEntry(model, key)
    override fun deleteEntry(key: String) = this@ModelMapPropertyImpl.deleteEntry(model, key)
    override fun changeEntryKey(old: String, new: String): ModelPropertyCore<ValueT> =
      this@ModelMapPropertyImpl.changeEntryKey(model, old, new)

    override val defaultValueGetter: (() -> Map<String, ValueT>?)? = null
    override val isModified: Boolean? get() = model.getParsedProperty()?.isModified

    override fun annotateParsedResolvedMismatch(): ValueAnnotation? = annotateParsedResolvedMismatchBy { parsedValue, resolvedValue ->
      if (parsedValue?.size != resolvedValue.size) false
      else parsedValue.all { (key, parsedValue) -> parsedValue == resolvedValue[key] }
    }

  }
}

private fun ResolvedPropertyModel?.asResolvedPropertiesMap(): Map<String, ResolvedPropertyModel>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.MAP }
    ?.getValue(GradlePropertyModel.MAP_TYPE)
    ?.mapValues { it.value.resolve() }

private fun <T : Any> ResolvedPropertyModel?.asParsedMapValue(
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean,
  modifiedSetter: () -> Unit,
  resolvedValues: Map<String, T>?
): Map<String, ModelPropertyCore<T>>? =
  this
    .asResolvedPropertiesMap()
    ?.mapValues {
      makeItemPropertyCore(
        it.value,
        getter,
        setter,
        { resolvedValues?.get(it.key)?.let { ResolvedValue.Set(it) } ?: ResolvedValue.NotResolved() },
        matcher,
        modifiedSetter)
    }

private fun <T : Any> ResolvedPropertyModel.addMapEntry(
  key: String,
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean,
  modifiedSetter: () -> Unit
): ModelPropertyCore<T> =
  makeItemPropertyCore(getMapValue(key).resolve(), getter, setter, { ResolvedValue.NotResolved() }, matcher, modifiedSetter)

private fun ResolvedPropertyModel.deleteMapEntry(key: String) = getMapValue(key).delete()

private fun <T : Any> ResolvedPropertyModel.changeMapEntryKey(
  old: String,
  new: String,
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean,
  modifiedSetter: () -> Unit
): ModelPropertyCore<T> {
  val oldProperty = getMapValue(old)
  // TODO(b/73057388): Simplify to plain oldProperty.getRawValue(OBJECT_TYPE).
  val oldValue = when (oldProperty.valueType) {
    GradlePropertyModel.ValueType.REFERENCE -> oldProperty.getRawValue(STRING_TYPE)?.let { ReferenceTo(it) }
    GradlePropertyModel.ValueType.UNKNOWN -> oldProperty.getRawValue(STRING_TYPE)?.let { ReferenceTo(it) }
    else -> oldProperty.getRawValue(OBJECT_TYPE)
  }

  oldProperty.delete()
  val newProperty = getMapValue(new)
  if (oldValue != null) newProperty.setValue(oldValue)
  // TODO(b/72814329): Match resolved value.
  return makeItemPropertyCore(newProperty.resolve(), getter, setter, { ResolvedValue.NotResolved() }, matcher, modifiedSetter)
}
