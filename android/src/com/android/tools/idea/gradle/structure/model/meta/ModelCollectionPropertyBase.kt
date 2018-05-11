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

abstract class ModelCollectionPropertyBase<in ContextT, in ModelT, out ResolvedT, ParsedT, in CollectionT, ValueT : Any> :
  ModelPropertyBase<ContextT, ModelT, ValueT>() {
  abstract val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>
  abstract val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel
  abstract val getter: ResolvedPropertyModel.() -> ValueT?
  abstract val setter: ResolvedPropertyModel.(ValueT) -> Unit

  fun setParsedValue(model: ModelT, value: ParsedValue<CollectionT>) {
    val parsedModel = modelDescriptor.getParsed(model) ?: throw IllegalStateException()
    val parsedProperty = parsedModel.parsedPropertyGetter()
    when (value) {
      is ParsedValue.NotSet -> {
        parsedProperty.delete()
      }
      is ParsedValue.Set.Parsed -> {
        val dsl = value.dslText
        when (dsl) {
          // Dsl modes.
          is DslText.Reference -> parsedProperty.setDslText(dsl)
          is DslText.InterpolatedString -> parsedProperty.setDslText(dsl)
          is DslText.OtherUnparsedDslText -> parsedProperty.setDslText(dsl)
          // Literal modes are not supported. getEditableValues() should be used.
          DslText.Literal -> throw UnsupportedOperationException()
        }
      }
      is ParsedValue.Set.Invalid -> throw IllegalArgumentException()
    }
    model.setModified()
  }

  protected fun ModelPropertyCore<ValueT>.makeSetModifiedAware(updatedModel: ModelT) = let {
    object : ModelPropertyCore<ValueT> by it {
      override fun setParsedValue(value: ParsedValue<ValueT>) {
        it.setParsedValue(value)
        modelDescriptor.setModified(updatedModel)
      }
    }
  }

  protected fun ModelPropertyParsedCore<ValueT>.makePropertyCore(resolvedValueGetter: () -> ValueT?): ModelPropertyCore<ValueT> =
    object : ModelPropertyCore<ValueT>, ModelPropertyParsedCore<ValueT> by this {
      override val defaultValueGetter: (() -> ValueT?)? = null

      override fun getResolvedValue(): ResolvedValue<ValueT> =
        resolvedValueGetter().let { if (it != null) ResolvedValue.Set(it) else ResolvedValue.NotResolved() }
    }

  protected fun ModelT.setModified() = modelDescriptor.setModified(this)
}

fun <T : Any> makeItemProperty(
  resolvedProperty: ResolvedPropertyModel,
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): ModelPropertyParsedCore<T> = object: ModelPropertyParsedCoreImpl<T>() {
  override fun getParsedProperty(): ResolvedPropertyModel? = resolvedProperty
  override val getter: ResolvedPropertyModel.() -> T? = getTypedValue
  override val setter: ResolvedPropertyModel.(T) -> Unit = setTypedValue
  override fun setModified() = Unit
}
