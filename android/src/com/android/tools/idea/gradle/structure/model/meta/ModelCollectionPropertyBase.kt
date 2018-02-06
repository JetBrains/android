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

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel

abstract class ModelCollectionPropertyBase<in ModelT, out ResolvedT, ParsedT, in CollectionT, ValueT : Any> {
  abstract val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>
  abstract val clearParsedValue: ParsedT.() -> Unit
  abstract val setParsedRawValue: (ParsedT.(DslText) -> Unit)
  abstract val parser: (String) -> ParsedValue<ValueT>
  abstract val knownValuesGetter: (ModelT) -> List<ValueDescriptor<ValueT>>?

  fun setParsedValue(model: ModelT, value: ParsedValue<CollectionT>) {
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


  fun parse(value: String): ParsedValue<ValueT> = parser(value)

  fun getKnownValues(model: ModelT): List<ValueDescriptor<ValueT>>? = knownValuesGetter(model)

  protected fun makeSetModifiedAware(
    it: ModelPropertyParsedCore<Unit, ValueT>,
    updatedModel: ModelT
  ) =
    object : ModelPropertyParsedCore<Unit, ValueT> by it {
      override fun setParsedValue(model: Unit, value: ParsedValue<ValueT>) {
        it.setParsedValue(Unit, value)
        modelDescriptor.setModified(updatedModel)
      }
    }

  protected fun makePropertyCore(it: ModelPropertyParsedCore<Unit, ValueT>, resolvedValueGetter: () -> ValueT?): ModelPropertyCore<Unit, ValueT> =
    object : ModelPropertyCore<Unit, ValueT>, ModelPropertyParsedCore<Unit, ValueT> by it {
      override fun getResolvedValue(model: Unit): ResolvedValue<ValueT> =
        resolvedValueGetter().let { if (it != null) ResolvedValue.Set(it) else ResolvedValue.NotResolved() }
    }
}

fun <T : Any> makeItemProperty(
  resolvedProperty: ResolvedPropertyModel,
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): ModelPropertyCore<Unit, T> =
  object : ModelPropertyCore<Unit, T> {
    override fun getParsedValue(model: Unit): ParsedValue<T> {
      val parsed: T? = resolvedProperty.getTypedValue()
      val dslText: DslText? = resolvedProperty.dslText()
      return when {
        (parsed == null && dslText == null) -> ParsedValue.NotSet<T>()
        parsed == null -> ParsedValue.Set.Invalid(dslText?.text.orEmpty(), "Unknown")
        else -> ParsedValue.Set.Parsed(value = parsed, dslText = dslText)
      }
    }

    override fun setParsedValue(model: Unit, value: ParsedValue<T>) {
      when (value) {
        is ParsedValue.NotSet -> resolvedProperty.delete()
        is ParsedValue.Set.Parsed -> {
          val dsl = value.dslText
          when (dsl?.mode) {
            // Dsl modes.
            DslMode.REFERENCE -> resolvedProperty.setDslText(dsl)
            DslMode.INTERPOLATED_STRING -> resolvedProperty.setDslText(dsl)
            DslMode.OTHER_UNPARSED_DSL_TEXT -> resolvedProperty.setDslText(dsl)
            // Literal modes.
            DslMode.LITERAL -> resolvedProperty.setTypedValue(value.value!!)
            null -> resolvedProperty.setTypedValue(value.value!!)
          }
        }
        is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
      }
    }

    override fun getResolvedValue(model: Unit): ResolvedValue<T> = ResolvedValue.NotResolved()
  }
