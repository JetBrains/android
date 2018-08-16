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
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty

/**
 * Makes a descriptor of a simple-typed property of a model of type [ModelT] described by the model descriptor.
 *
 * @param description the description of the property as it should appear int he UI
 * @param defaultValueGetter the function returning the default value of the property for the given model (overwrites [default] if
 *        defined)
 * @param resolvedValueGetter the function to get the value of the property as it was resolved by Gradle
 * @param parsedPropertyGetter the function to get the [ResolvedPropertyModel] of the property of the parsed model
 * @param getter the getter function to get the value of the [ResolvedPropertyModel]
 * @param setter the setter function to change the value of the [ResolvedPropertyModel]
 * @param parser the parser of the text representation of [PropertyT]. See notes in: [ModelSimpleProperty]
 * @param formatter the formatter for values of type [PropertyT]
 * @param knownValuesGetter the function to get a list of the known value for the given instance of [ModelT]. See: [ModelSimpleProperty]
 */
// NOTE: This is an extension function supposed to be invoked on model descriptors to make the type inference work.
fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  ModelT,
  ResolvedT,
  ParsedT,
  PropertyT : Any> T.property(
  description: String,
  defaultValueGetter: ((ModelT) -> PropertyT?)? = null,
  resolvedValueGetter: ResolvedT.() -> PropertyT?,
  parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  getter: ResolvedPropertyModel.() -> PropertyT?,
  setter: ResolvedPropertyModel.(PropertyT) -> Unit,
  parser: (String) -> Annotated<ParsedValue<PropertyT>>,
  formatter: (PropertyT) -> String = { it.toString() },
  knownValuesGetter: ((ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>) = { immediateFuture(listOf()) },
  variableMatchingStrategy: VariableMatchingStrategy = VariableMatchingStrategy.BY_TYPE,
  matcher: (model: ModelT, parsedValue: PropertyT?, resolvedValue: PropertyT) -> Boolean =
    { _, parsedValue, resolvedValue -> parsedValue == resolvedValue }
): ModelSimpleProperty<ModelT, PropertyT> = ModelSimplePropertyImpl(
  this,
  description,
  defaultValueGetter,
  resolvedValueGetter,
  parsedPropertyGetter,
  getter,
  setter,
  parser,
  formatter,
  knownValuesGetter,
  variableMatchingStrategy,
  matcher
)

class ModelSimplePropertyImpl<in ModelT, ResolvedT, ParsedT, PropertyT : Any>(
  private val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  val defaultValueGetter: ((ModelT) -> PropertyT?)?,
  private val resolvedValueGetter: ResolvedT.() -> PropertyT?,
  private val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  private val getter: ResolvedPropertyModel.() -> PropertyT?,
  private val setter: ResolvedPropertyModel.(PropertyT) -> Unit,
  override val parser: (String) -> Annotated<ParsedValue<PropertyT>>,
  override val formatter: (PropertyT) -> String,
  override val knownValuesGetter: (ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>,
  override val variableMatchingStrategy: VariableMatchingStrategy,
  private val matcher: (model: ModelT, parsed: PropertyT?, resolved: PropertyT) -> Boolean
) : ModelPropertyBase<ModelT, PropertyT>(),
    ModelSimpleProperty<ModelT, PropertyT> {
  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<PropertyT> =
    getParsedValue(modelDescriptor.getParsed(thisRef)?.parsedPropertyGetter(), getter).value

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<PropertyT>) {
    thisRef.setModified()
    setParsedValue((modelDescriptor.getParsed(thisRef) ?: throw IllegalStateException()).parsedPropertyGetter(),
                   setter,
                   { delete() },
                   value)
  }

  inner class SimplePropertyCore(private val model: ModelT)
    : ModelPropertyCoreImpl<PropertyT>(),
      ModelPropertyCore<PropertyT> {
    override val description: String = this@ModelSimplePropertyImpl.description
    override fun getParsedProperty(): ResolvedPropertyModel? = modelDescriptor.getParsed(model)?.parsedPropertyGetter()
    override val getter: ResolvedPropertyModel.() -> PropertyT? = this@ModelSimplePropertyImpl.getter
    override val setter: ResolvedPropertyModel.(PropertyT) -> Unit = this@ModelSimplePropertyImpl.setter
    override val nullifier: ResolvedPropertyModel.() -> Unit = { delete() }
    override fun setModified() = modelDescriptor.setModified(model)
    override fun getResolvedValue(): ResolvedValue<PropertyT> {
      val resolvedModel = modelDescriptor.getResolved(model)
      val resolved: PropertyT? = resolvedModel?.resolvedValueGetter()
      return when (resolvedModel) {
        null -> ResolvedValue.NotResolved()
        else -> ResolvedValue.Set(resolved)
      }
    }

    override val defaultValueGetter: (() -> PropertyT?)? = this@ModelSimplePropertyImpl.defaultValueGetter?.let { { it(model) } }
    override fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean =
      matcher(model, parsedValue, resolvedValue)

  }

  override fun bind(model: ModelT): ModelPropertyCore<PropertyT> = SimplePropertyCore(model)

  private fun ModelT.setModified() = modelDescriptor.setModified(this)
}

abstract class ModelPropertyCoreImpl<PropertyT : Any>
  : ModelPropertyCore<PropertyT>, GradleModelCoreProperty<PropertyT, ModelPropertyCore<PropertyT>> {
  abstract val getter: ResolvedPropertyModel.() -> PropertyT?
  abstract val setter: ResolvedPropertyModel.(PropertyT) -> Unit
  abstract val nullifier: ResolvedPropertyModel.() -> Unit
  abstract fun setModified()

  override fun getParsedValue(): Annotated<ParsedValue<PropertyT>> = getParsedValue(getParsedProperty(), getter)

  override fun setParsedValue(value: ParsedValue<PropertyT>) {
    setModified()
    setParsedValue(getParsedProperty() ?: throw IllegalStateException(), setter, nullifier, value)
  }

  override val isModified: Boolean? get() = getParsedProperty()?.isModified

  override fun annotateParsedResolvedMismatch(): ValueAnnotation? =
    annotateParsedResolvedMismatchBy { parsedValueToCompare, resolvedValue ->
      parsedAndResolvedValuesAreEqual(parsedValueToCompare, resolvedValue)
    }

  abstract fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean

  override fun rebind(resolvedProperty: ResolvedPropertyModel, modifiedSetter: () -> Unit): ModelPropertyCore<PropertyT> {
    return object : ModelPropertyCoreImpl<PropertyT>(),
                    ModelPropertyCore<PropertyT>,
                    GradleModelCoreProperty<PropertyT, ModelPropertyCore<PropertyT>> {
      override val description: String = this@ModelPropertyCoreImpl.description
      override fun getParsedProperty(): ResolvedPropertyModel? = resolvedProperty
      override val getter: ResolvedPropertyModel.() -> PropertyT? = this@ModelPropertyCoreImpl.getter
      override val setter: ResolvedPropertyModel.(PropertyT) -> Unit = this@ModelPropertyCoreImpl.setter
      override val nullifier: ResolvedPropertyModel.() -> Unit = { delete() }
      override fun setModified() = modifiedSetter()
      override fun getResolvedValue(): ResolvedValue<PropertyT> = ResolvedValue.NotResolved()

      override val defaultValueGetter: (() -> PropertyT?)? = null
      override fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean =
        throw UnsupportedOperationException()

      override fun rebind(resolvedProperty: ResolvedPropertyModel, modifiedSetter: () -> Unit): ModelPropertyCore<PropertyT> =
        this@ModelPropertyCoreImpl.rebind(resolvedProperty, modifiedSetter)
    }
  }
}

private fun <T : Any> getParsedValue(property: ResolvedPropertyModel?, getter: ResolvedPropertyModel.() -> T?): Annotated<ParsedValue<T>> =
  makeAnnotatedParsedValue(property?.getter(), property?.dslText())

private fun <T : Any> setParsedValue(parsedProperty: ResolvedPropertyModel,
                                     setter: ResolvedPropertyModel.(T) -> Unit,
                                     nullifier: ResolvedPropertyModel.() -> Unit,
                                     value: ParsedValue<T>) {
  when (value) {
    is ParsedValue.NotSet -> {
      parsedProperty.nullifier()
    }
    is ParsedValue.Set.Parsed -> {
      val dsl = value.dslText
      when (dsl) {
      // Dsl modes.
        is DslText.Reference -> parsedProperty.setDslText(dsl)
        is DslText.InterpolatedString -> parsedProperty.setDslText(dsl)
        is DslText.OtherUnparsedDslText -> parsedProperty.setDslText(dsl)
      // Literal modes.
        DslText.Literal -> if (value.value != null) {
          parsedProperty.setter(value.value)
        }
        else {
          parsedProperty.nullifier()
        }
      }
    }
  }
}
