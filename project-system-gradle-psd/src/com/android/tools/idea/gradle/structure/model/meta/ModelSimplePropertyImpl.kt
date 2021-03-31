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
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.util.PatternUtil
import java.io.File
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
  preferredVariableName: ModelT.() -> String = { "var" },
  defaultValueGetter: ((ModelT) -> PropertyT?)? = null,
  variableScope: (ModelT.() -> PsVariablesScope)? = null,
  resolvedValueGetter: ResolvedT.() -> PropertyT?,
  parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel?,
  parsedPropertyInitializer: ParsedT.() -> ResolvedPropertyModel = {
    throw UnsupportedOperationException("Property '$description' cannot be automatically initialized.")
  },
  getter: ResolvedPropertyModel.() -> PropertyT?,
  setter: ResolvedPropertyModel.(PropertyT) -> Unit,
  refresher: ModelT.() -> Unit = {},
  parser: (String) -> Annotated<ParsedValue<PropertyT>>,
  formatter: (PropertyT) -> String = { it.toString() },
  knownValuesGetter: ((ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>) = { immediateFuture(listOf()) },
  variableMatchingStrategy: VariableMatchingStrategy = VariableMatchingStrategy.BY_TYPE,
  matcher: (model: ModelT, parsedValue: PropertyT?, resolvedValue: PropertyT) -> Boolean =
    { _, parsedValue, resolvedValue -> parsedValue == resolvedValue }
): ModelSimpleProperty<ModelT, PropertyT> = ModelSimplePropertyImpl(
  this,
  description,
  preferredVariableName,
  defaultValueGetter,
  variableScope,
  resolvedValueGetter,
  parsedPropertyGetter,
  parsedPropertyInitializer,
  getter,
  setter,
  refresher,
  parser,
  formatter,
  knownValuesGetter,
  variableMatchingStrategy,
  matcher
)

/**
 * Attaches file chooser details to the property.
 */
fun <ModelT, PropertyT : Any> ModelSimpleProperty<ModelT, PropertyT>.withFileSelectionRoot(
  masks: List<String>? = null,
  browseRoot: (ModelT) -> File?,
  resolveRoot: (ModelT) -> File?
) =
  let { baseProperty ->
    object : ModelSimpleProperty<ModelT, PropertyT> by baseProperty {
      override fun bindContext(model: ModelT): ModelPropertyContext<PropertyT> =
        object : FileTypePropertyContext<PropertyT>, ModelPropertyContext<PropertyT> by baseProperty.bindContext(model) {
          override val browseRootDir: File? = browseRoot(model)
          override val resolveRootDir: File? = resolveRoot(model)
          override val filterPredicate: ((File) -> Boolean)? = masks?.toPredicate()
        }
    }
  }

/**
 * Attaches file chooser details to the property.
 */
fun <ModelT, PropertyT : Any> ModelListProperty<ModelT, PropertyT>.withFileSelectionRoot(
  masks: List<String>? = null,
  browseRoot: ModelT.() -> File?,
  resolveRoot: ModelT.() -> File?
) =
  let { baseProperty ->
    object : ModelListProperty<ModelT, PropertyT> by baseProperty {
      override fun bindContext(model: ModelT): ModelPropertyContext<PropertyT> =
        object : FileTypePropertyContext<PropertyT>, ModelPropertyContext<PropertyT> by baseProperty.bindContext(model) {
          override val browseRootDir: File? = model.browseRoot()
          override val resolveRootDir: File? = model.resolveRoot()
          override val filterPredicate: ((File) -> Boolean)? = masks?.toPredicate()
        }
    }
  }

private fun List<String>.toPredicate(): (File) -> Boolean =
  map { PatternUtil.fromMask(it) }
    .let { patterns ->
      { probe: File -> patterns.any { pattern -> pattern.matcher(probe.name).matches() } }
    }

class ModelSimplePropertyImpl<in ModelT, ResolvedT, ParsedT, PropertyT : Any>(
  private val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  val preferredVariableName: ModelT.() -> String,
  val defaultValueGetter: ((ModelT) -> PropertyT?)?,
  val variableScope: ((ModelT) -> PsVariablesScope)?,
  private val resolvedValueGetter: ResolvedT.() -> PropertyT?,
  private val parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel?,
  private val parsedPropertyInitializer: ParsedT.() -> ResolvedPropertyModel,
  private val getter: ResolvedPropertyModel.() -> PropertyT?,
  private val setter: ResolvedPropertyModel.(PropertyT) -> Unit,
  private val refresher: ModelT.() -> Unit,
  override val parser: (String) -> Annotated<ParsedValue<PropertyT>>,
  override val formatter: (PropertyT) -> String,
  override val knownValuesGetter: (ModelT) -> ListenableFuture<List<ValueDescriptor<PropertyT>>>,
  override val variableMatchingStrategy: VariableMatchingStrategy,
  private val matcher: (model: ModelT, parsed: PropertyT?, resolved: PropertyT) -> Boolean
) : ModelPropertyBase<ModelT, PropertyT>(),
    ModelSimpleProperty<ModelT, PropertyT> {
  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<PropertyT> =
    modelDescriptor.getParsed(thisRef)?.parsedPropertyGetter().getParsedValue(getter).value

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<PropertyT>) {
    thisRef.modify {
      // modify() is expected to instantiate a parsed model if it is still null.
      (modelDescriptor.getParsed(thisRef) ?: throw IllegalStateException()).let {
        (it.parsedPropertyGetter() ?: it.parsedPropertyInitializer()).setParsedValue(setter, { delete() }, value)
      }
    }
  }

  inner class SimplePropertyCore(private val model: ModelT)
    : ModelPropertyCoreImpl<PropertyT>(),
      ModelPropertyCore<PropertyT> {
    override val description: String = this@ModelSimplePropertyImpl.description
    override fun getPreferredVariableName(): String = model.preferredVariableName()
    override fun getParsedPropertyForRead(): ResolvedPropertyModel? = modelDescriptor.getParsed(model)?.parsedPropertyGetter()
    override fun getParsedPropertyForWrite(): ResolvedPropertyModel =
      modelDescriptor.getParsed(model)?.let { it.parsedPropertyGetter() ?: it.parsedPropertyInitializer() }!!
    override val getter: ResolvedPropertyModel.() -> PropertyT? = this@ModelSimplePropertyImpl.getter
    override val setter: ResolvedPropertyModel.(PropertyT) -> Unit = this@ModelSimplePropertyImpl.setter
    override val nullifier: ResolvedPropertyModel.() -> Unit = { delete() }

    override fun modify(block: () -> Unit) = model.modify{ block() }

    override fun getResolvedValue(): ResolvedValue<PropertyT> {
      val resolvedModel = modelDescriptor.getResolved(model)
      val resolved: PropertyT? = resolvedModel?.resolvedValueGetter()
      return when (resolvedModel) {
        null -> ResolvedValue.NotResolved()
        else -> ResolvedValue.Set(resolved)
      }
    }

    override val defaultValueGetter: (() -> PropertyT?)? = this@ModelSimplePropertyImpl.defaultValueGetter?.let { { it(model) } }
    override val variableScope: (() -> PsVariablesScope?)? = this@ModelSimplePropertyImpl.variableScope?.let { { it(model) } }
    override fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean =
      matcher(model, parsedValue, resolvedValue)

  }

  override fun bind(model: ModelT): ModelPropertyCore<PropertyT> = SimplePropertyCore(model)

  private fun ModelT.modify(block: ModelT.() -> Unit) {
    modelDescriptor.prepareForModification(this)
    block()
    modelDescriptor.setModified(this)
    this.refresher()
  }
}

abstract class ModelPropertyCoreImpl<PropertyT : Any>
  : ModelPropertyCore<PropertyT>, GradleModelCoreProperty<PropertyT, ModelPropertyCore<PropertyT>> {
  abstract val getter: ResolvedPropertyModel.() -> PropertyT?
  abstract val setter: ResolvedPropertyModel.(PropertyT) -> Unit
  abstract val nullifier: ResolvedPropertyModel.() -> Unit
  abstract fun modify(block: () -> Unit)

  override fun getParsedValue(): Annotated<ParsedValue<PropertyT>> = getParsedPropertyForRead().getParsedValue(getter)

  override fun setParsedValue(value: ParsedValue<PropertyT>) {
    modify {
      getParsedPropertyForWrite().setParsedValue(setter, nullifier, value)
    }
  }

  override val isModified: Boolean? get() = getParsedPropertyForRead()?.isModified

  override fun annotateParsedResolvedMismatch(): ValueAnnotation? =
    annotateParsedResolvedMismatchBy { parsedValueToCompare, resolvedValue ->
      parsedAndResolvedValuesAreEqual(parsedValueToCompare, resolvedValue)
    }

  abstract fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean

  override fun rebind(
    resolvedProperty: ResolvedPropertyModel,
    modifier: (() -> Unit) -> Unit
  ): ModelPropertyCore<PropertyT> {
    return object : ModelPropertyCoreImpl<PropertyT>(),
                    ModelPropertyCore<PropertyT>,
                    GradleModelCoreProperty<PropertyT, ModelPropertyCore<PropertyT>> {
      override val description: String = this@ModelPropertyCoreImpl.description
      override fun getPreferredVariableName(): String = this@ModelPropertyCoreImpl.getPreferredVariableName()
      override fun getParsedPropertyForRead(): ResolvedPropertyModel? = resolvedProperty
      override fun getParsedPropertyForWrite(): ResolvedPropertyModel = resolvedProperty
      override val getter: ResolvedPropertyModel.() -> PropertyT? = this@ModelPropertyCoreImpl.getter
      override val setter: ResolvedPropertyModel.(PropertyT) -> Unit = this@ModelPropertyCoreImpl.setter
      override val nullifier: ResolvedPropertyModel.() -> Unit = { delete() }
      override fun modify(block: () -> Unit) = modifier(block)
      override fun getResolvedValue(): ResolvedValue<PropertyT> = ResolvedValue.NotResolved()

      override val defaultValueGetter: (() -> PropertyT?)? = null
      override val variableScope: (() -> PsVariablesScope?)? = null
      override fun parsedAndResolvedValuesAreEqual(parsedValue: PropertyT?, resolvedValue: PropertyT): Boolean =
        throw UnsupportedOperationException()

      override fun rebind(
        resolvedProperty: ResolvedPropertyModel,
        modifier: (() -> Unit) -> Unit
      ): ModelPropertyCore<PropertyT> =
        this@ModelPropertyCoreImpl.rebind(resolvedProperty, modifier)
    }
  }
}
