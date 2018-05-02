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

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty

/**
 * Makes a descriptor of a simple-typed property of a model of type [ModelT] described by the model descriptor.
 *
 * @param description the description of the property as it should appear int he UI
 * @param default the default value the property takes if not configured or null
 * @param defaultValueGetter the function returning the default value of the property for the given model (overwrites [default] if
 *        defined)
 * @param getResolvedValue the function to get the value of the property as it was resolved by Gradle
 * @param getParsedProperty the function to get the [ResolvedPropertyModel] of the property of the parsed model
 * @param getter the getter function to get the value of the [ResolvedPropertyModel]
 * @param setter the setter function to change the value of the [ResolvedPropertyModel]
 * @param parse the parser of the text representation of [PropertyT]. See notes in: [ModelSimpleProperty]
 * @param format the formatter for values of type [PropertyT]
 * @param getKnownValues the function to get a list of the known value for the given instance of [ModelT]. See: [ModelSimpleProperty]
 */
// NOTE: This is an extension function supposed to be invoked on model descriptors to make the type inference work.
fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>,
    ModelT,
    ResolvedT,
    ParsedT,
    ResolvedPropertyModelT : ResolvedPropertyModel,
    PropertyT : Any,
    ContextT> T.property(
  description: String,
  default: PropertyT? = null,
  defaultValueGetter: (ModelT) -> PropertyT? = { default },
  getResolvedValue: ResolvedT.() -> PropertyT?,
  getParsedProperty: ParsedT.() -> ResolvedPropertyModelT,
  getter: ResolvedPropertyModelT.() -> PropertyT?,
  setter: ResolvedPropertyModelT.(PropertyT) -> Unit,
  parse: (ContextT, String) -> ParsedValue<PropertyT>,
  format: (ContextT, PropertyT) -> String = { _, value -> value.toString() },
  getKnownValues: ((ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>)? = null
) =
  ModelSimplePropertyImpl(
    this,
    description,
    defaultValueGetter,
    getResolvedValue,
    { getParsedProperty().getter() },
    { getParsedProperty().dslText() },
    { if (it != null) getParsedProperty().setter(it) else getParsedProperty().delete() },
    { getParsedProperty().setDslText(it) },
    { context: ContextT, value: String -> if (value.isBlank()) ParsedValue.NotSet else parse(context, value.trim()) },
    format,
    { context: ContextT, model: ModelT -> if (getKnownValues != null) getKnownValues(context, model) else immediateFuture(listOf()) }
  )

/**
 * Makes a descriptor of a simple-typed property of a model of type [ModelT] described by the model descriptor.
 *
 * @param description the description of the property as it should appear int he UI
 * @param default the default value the property takes if not configured or null
 * @param defaultValueGetter the function returning the default value of the property for the given model (overwrites [default] if
 *        defined)
 * @param getResolvedValue the function to get the value of the property as it was resolved by Gradle
 * @param getParsedValue the function to get the value of the property as it was parsed
 * @param setParsedValue the setter function to change the value of the property in the build files
 * @param clearParsedValue the function to remove the configuration of the property from the build files
 * @param parse the parser of the text representation of [PropertyT]. See notes in: [ModelSimpleProperty]
 * @param format the formatter for values of type [PropertyT]
 * @param getKnownValues the function to get a list of the known value for the given instance of [ModelT]. See: [ModelSimpleProperty]
 */
// NOTE: This is an extension function supposed to be invoked on model descriptors to make the type inference work.
fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, PropertyT : Any, ContextT> T.property(
    description: String,
    default: PropertyT? = null,
    defaultValueGetter: (ModelT) -> PropertyT? = { default },
    getResolvedValue: ResolvedT.() -> PropertyT?,
    getParsedValue: ParsedT.() -> PropertyT?,
    getParsedRawValue: ParsedT.() -> DslText?,
    setParsedValue: ParsedT.(PropertyT) -> Unit,
    setParsedRawValue: (ParsedT.(DslText) -> Unit)? = null,
    clearParsedValue: ParsedT.() -> Unit,
    parse: (ContextT, String) -> ParsedValue<PropertyT>,
    format: (ContextT, PropertyT) -> String = { _, value -> value.toString() },
    getKnownValues: ((ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>)? = null
) =
    ModelSimplePropertyImpl(
        this,
        description,
        defaultValueGetter,
        getResolvedValue,
        getParsedValue,
        getParsedRawValue,
        { if (it != null) setParsedValue(it) else clearParsedValue() },
        {
          when {
            setParsedRawValue == null -> throw UnsupportedOperationException("setParsedRawValue is undefined for property '$description'")
            it is DslText.Reference -> setParsedRawValue(it)
            it is DslText.InterpolatedString -> setParsedRawValue(it)
            it is DslText.OtherUnparsedDslText -> setParsedRawValue(it)
            else -> throw UnsupportedOperationException()
          }
        },
        { context: ContextT, value -> if (value.isBlank()) ParsedValue.NotSet else parse(context, value.trim()) },
        format,
        { context: ContextT, model -> if (getKnownValues != null) getKnownValues(context, model) else immediateFuture(listOf()) }
    )

class ModelSimplePropertyImpl<in ContextT, in ModelT, ResolvedT, ParsedT, PropertyT : Any>(
    private val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
    override val description: String,
    private val defaultValueGetter: (ModelT) -> PropertyT?,
    private val getResolvedValue: ResolvedT.() -> PropertyT?,
    private val getParsedValue: ParsedT.() -> PropertyT?,
    private val getParsedRawValue: ParsedT.() -> DslText?,
    private val setParsedValue: (ParsedT.(PropertyT?) -> Unit),
    private val setParsedRawValue: (ParsedT.(DslText) -> Unit),
    private val parser: (ContextT, String) -> ParsedValue<PropertyT>,
    private val formatter: (ContextT, PropertyT) -> String,
    private val knownValuesGetter: (ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>
) : ModelSimpleProperty<ContextT, ModelT, PropertyT> {
  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<PropertyT> = getParsedValue(thisRef)

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<PropertyT>) = setParsedValue(thisRef, value)

  override fun getParsedValue(model: ModelT): ParsedValue<PropertyT> {
    val parsedModel = modelDescriptor.getParsed(model)
    return makeParsedValue(parsedModel?.getParsedValue(), parsedModel?.getParsedRawValue())
  }

  override fun getResolvedValue(model: ModelT): ResolvedValue<PropertyT> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: PropertyT? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun setParsedValue(model: ModelT, value: ParsedValue<PropertyT>) {
    val parsedModel = modelDescriptor.getParsed(model) ?: throw IllegalStateException()
    when (value) {
      is ParsedValue.NotSet -> parsedModel.setParsedValue(null)
      is ParsedValue.Set.Parsed -> {
        val dsl = value.dslText
        when (dsl) {
          // Dsl modes.
          is DslText.Reference -> parsedModel.setParsedRawValue(dsl)
          is DslText.InterpolatedString -> parsedModel.setParsedRawValue(dsl)
          is DslText.OtherUnparsedDslText -> parsedModel.setParsedRawValue(dsl)
          // Literal modes.
          DslText.Literal -> parsedModel.setParsedValue(value.value)
        }
      }
      is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
    }
    // TODO: handle the case of "debug" which is always present and thus might not have a parsed model.
    model.setModified()
  }

  override fun getDefaultValue(model: ModelT): PropertyT? = defaultValueGetter(model)

  override fun parse(context: ContextT, value: String): ParsedValue<PropertyT> = parser(context, value)

  override fun format(context: ContextT, value: PropertyT): String = formatter(context, value)

  override fun getKnownValues(context: ContextT, model: ModelT): ListenableFuture<List<ValueDescriptor<PropertyT>>> =
    knownValuesGetter(context, model)

  private fun ModelT.setModified() = modelDescriptor.setModified(this)
}

