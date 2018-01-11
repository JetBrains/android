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
 * @param getKnownValues the function to get a list of the known value for the given instance of [ModelT]. See: [ModelSimpleProperty]
 */
// NOTE: This is an extension function supposed to be invoked on model descriptors to make the type inference work.
fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, PropertyT : Any> T.property(
    description: String,
    default: PropertyT? = null,
    defaultValueGetter: (ModelT) -> PropertyT? = { default },
    getResolvedValue: ResolvedT.() -> PropertyT?,
    getParsedValue: ParsedT.() -> PropertyT?,
    getParsedRawValue: ParsedT.() -> String?,
    setParsedValue: ParsedT.(PropertyT) -> Unit,
    clearParsedValue: ParsedT.() -> Unit,
    parse: (String) -> ParsedValue<PropertyT>,
    getKnownValues: ((ModelT) -> List<ValueDescriptor<PropertyT>>)? = null
) =
    ModelSimplePropertyImpl(
        this,
        description,
        defaultValueGetter,
        getResolvedValue,
        getParsedValue,
        getParsedRawValue,
        { if (it != null) setParsedValue(it) else clearParsedValue() },
        { if (it.isBlank()) ParsedValue.NotSet() else parse(it.trim()) },
        { if (getKnownValues != null) getKnownValues(it) else null }
    )

class ModelSimplePropertyImpl<in ModelT, ResolvedT, ParsedT, PropertyT : Any>(
    private val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
    override val description: String,
    private val defaultValueGetter: (ModelT) -> PropertyT?,
    private val getResolvedValue: ResolvedT.() -> PropertyT?,
    private val getParsedValue: ParsedT.() -> PropertyT?,
    private val getParsedRawValue: ParsedT.() -> String?,
    private val setParsedValue: (ParsedT.(PropertyT?) -> Unit),
    private val parser: (String) -> ParsedValue<PropertyT>,
    private val knownValuesGetter: (ModelT) -> List<ValueDescriptor<PropertyT>>?
) : ModelSimpleProperty<ModelT, PropertyT> {
  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<PropertyT> = getValue(thisRef).parsedValue

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<PropertyT>) = setValue(thisRef, value)

  override fun getValue(model: ModelT): PropertyValue<PropertyT> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: PropertyT? = resolvedModel?.getResolvedValue()
    val parsedModel = modelDescriptor.getParsed(model)
    val parsed: PropertyT? = parsedModel?.getParsedValue()
    val dslText: String? = parsedModel?.getParsedRawValue()
    val parsedValue = when {
      (parsed == null && dslText == null) -> ParsedValue.NotSet<PropertyT>()
      parsed == null -> ParsedValue.Set.Invalid(dslText.orEmpty(), "Unknown")
      else -> ParsedValue.Set.Parsed(
          value = parsed,
          dslText = dslText)
    }
    val resolvedValue = when (resolvedModel) {
      null -> ResolvedValue.NotResolved<PropertyT>()
      else -> ResolvedValue.Set(resolved)
    }
    return PropertyValue(parsedValue, resolvedValue)
  }

  override fun setValue(model: ModelT, value: ParsedValue<PropertyT>) {
    val valueToSet: PropertyT? = when (value) {
      is ParsedValue.NotSet -> null
      is ParsedValue.Set.Parsed -> value.value
      is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
    }
    // TODO: handle the case of "debug" which is always present and thus might not have a parsed model.
    val parsedModel = modelDescriptor.getParsed(model) ?: throw IllegalStateException()
    parsedModel.setParsedValue(valueToSet)
    model.setModified()
  }

  override fun getDefaultValue(model: ModelT): PropertyT? = defaultValueGetter(model)

  override fun parse(value: String): ParsedValue<PropertyT> = parser(value)

  override fun getKnownValues(model: ModelT): List<ValueDescriptor<PropertyT>>? = knownValuesGetter(model)

  private fun ModelT.setModified() = modelDescriptor.setModified(this)
}

fun ResolvedPropertyModel.asString() =
    getValue(GradlePropertyModel.STRING_TYPE) ?:
        getValue(GradlePropertyModel.INTEGER_TYPE)?.toString()

fun ResolvedPropertyModel.asInt() = getValue(GradlePropertyModel.INTEGER_TYPE)
fun ResolvedPropertyModel.asBoolean() = getValue(GradlePropertyModel.BOOLEAN_TYPE)
fun ResolvedPropertyModel.dslText() = getRawValue(GradlePropertyModel.STRING_TYPE)
