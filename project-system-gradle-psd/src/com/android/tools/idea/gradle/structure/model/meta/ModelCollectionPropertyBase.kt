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
import com.android.tools.idea.gradle.structure.model.PsVariablesScope

abstract class ModelCollectionPropertyBase<ModelT, out ResolvedT, ParsedT, in CollectionT : Any, ValueT : Any> :
  ModelPropertyBase<ModelT, ValueT>() {
  abstract val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>
  abstract val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel
  abstract val getter: ResolvedPropertyModel.() -> ValueT?
  abstract val setter: ResolvedPropertyModel.(ValueT) -> Unit

  protected fun ModelT.getParsedProperty(): ResolvedPropertyModel? = modelDescriptor
      .getParsed(this)
      ?.parsedPropertyGetter()

  fun setParsedValue(model: ModelT, value: ParsedValue<CollectionT>) {
    model.modify {
      val parsedProperty = model.getParsedProperty() ?: throw IllegalStateException()
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
      }
    }
  }

  protected fun <T> ModelT.modify(modifier: ModelT.() -> T) : T {
    modelDescriptor.prepareForModification(this)
    val result = modifier()
    modelDescriptor.setModified(this)
    return result
  }
}

fun <T : Any> makeItemPropertyCore(
  resolvedProperty: ResolvedPropertyModel,
  getter: ResolvedPropertyModel.() -> T?,
  setter: ResolvedPropertyModel.(T) -> Unit,
  resolvedValueGetter: () -> ResolvedValue<T>,
  matcher: (parsedValue: T?, resolvedValue: T)-> Boolean,
  modifier: (() -> Unit) -> Unit
): ModelPropertyCore<T> = object: ModelPropertyCoreImpl<T>(), ModelPropertyCore<T> {
  override val description: String get() = ""
  override fun getParsedPropertyForRead(): ResolvedPropertyModel? = resolvedProperty
  override fun getParsedPropertyForWrite(): ResolvedPropertyModel = resolvedProperty
  override val getter: ResolvedPropertyModel.() -> T? = getter
  override val setter: ResolvedPropertyModel.(T) -> Unit = setter
  override val nullifier: ResolvedPropertyModel.() -> Unit = { setValue("") }
  override fun modify(block: () -> Unit) = modifier(block)
  override fun getResolvedValue(): ResolvedValue<T> = resolvedValueGetter()
  override val defaultValueGetter: (() -> T?)? = null
  override val variableScope: (() -> PsVariablesScope?)? = null
  override val isModified: Boolean? get() = resolvedProperty.isModified
  override fun parsedAndResolvedValuesAreEqual(parsedValue: T?, resolvedValue: T): Boolean = matcher(parsedValue, resolvedValue)
}
