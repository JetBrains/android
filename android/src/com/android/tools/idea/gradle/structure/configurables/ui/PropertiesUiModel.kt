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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.properties.*
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.*

/**
 * A model of the UI for editing the properties of a model of type [ModelT].
 *
 * This is the basic UI model defining a set of properties to be edited and their order.
 */
class PropertiesUiModel<in ModelT>(val properties: List<PropertyUiModel<ModelT, *>>)

/**
 * A UI model of a property of type [PropertyT] of a model of type [ModelT].
 */
interface PropertyUiModel<in ModelT, out PropertyT : Any> {
  /**
   * The plain text description of the property as it should appear in the UI.
   */
  val propertyDescription: String

  /**
   * Creates a property editor bound to a property of [model] which described by this model.
   */
  fun createEditor(project: PsProject, module: PsModule, model: ModelT): ModelPropertyEditor<PropertyT>
}

typealias
  PropertyEditorFactory<ModelT, ModelPropertyT, PropertyT> =
  (project: PsProject, module: PsModule?, model: ModelT, ModelPropertyT, PsVariablesScope?) ->
  ModelPropertyEditor<PropertyT>

typealias
  PropertyEditorCoreFactory<ModelPropertyCoreT, ModelPropertyContextT, PropertyT> =
  (ModelPropertyCoreT, ModelPropertyContextT, PsVariablesScope?) -> ModelPropertyEditor<PropertyT>

/**
 * Creates a UI property model describing how to represent [property] for editing.
 *
 * @param editorFactory the function to create an editor bound to [property]
 */
fun <ModelT, PropertyT : Any, ValueT : Any, ModelPropertyCoreT : ModelPropertyCore<PropertyT>,
  ModelPropertyT : ModelProperty<ModelT, PropertyT, ValueT, ModelPropertyCoreT>> uiProperty(
  property: ModelPropertyT,
  editorFactory: PropertyEditorFactory<ModelT, ModelPropertyT, PropertyT>
): PropertyUiModel<ModelT, *> =
  PropertyUiModelImpl(property, editorFactory)

class PropertyUiModelImpl<
  in ModelT, PropertyT : Any,
  ValueT : Any,
  out ModelPropertyCoreT : ModelPropertyCore<PropertyT>,
  out ModelPropertyT : ModelProperty<ModelT, PropertyT, ValueT, ModelPropertyCoreT>>
(
  private val property: ModelPropertyT,
  private val editorFactory: PropertyEditorFactory<ModelT, ModelPropertyT, PropertyT>
) : PropertyUiModel<ModelT, PropertyT> {
  override val propertyDescription: String = property.description
  override fun createEditor(project: PsProject, module: PsModule, model: ModelT)
    : ModelPropertyEditor<PropertyT> {
    return editorFactory(project, module, model, property, module.variables)
  }
}

fun <T : Any, PropertyCoreT : ModelPropertyCore<T>> createDefaultEditorExtensions(
  project: PsProject,
  module: PsModule?
): List<EditorExtensionAction<T, PropertyCoreT>> =
  if (module != null) listOf(ExtractNewVariableExtension(project, module))
  else listOf()

fun <ModelT, ValueT : Any, ModelPropertyT : ModelProperty<ModelT, ValueT, ValueT, ModelPropertyCore<ValueT>>>
  simplePropertyEditor(
  project: PsProject,
  module: PsModule?,
  model: ModelT,
  property: ModelPropertyT,
  variablesScope: PsVariablesScope? = null
): SimplePropertyEditor<ValueT, ModelPropertyCore<ValueT>> {
  val boundProperty = property.bind(model)
  val boundContext = property.bindContext(model)
  return SimplePropertyEditor(boundProperty, boundContext, variablesScope, createDefaultEditorExtensions(project, module))
}

@Suppress("UNUSED_PARAMETER")
fun <ModelT, ValueT : Any, ModelPropertyT : ModelListProperty<ModelT, ValueT>> listPropertyEditor(
  project: PsProject,
  module: PsModule?,
  model: ModelT,
  property: ModelPropertyT,
  variablesScope: PsVariablesScope? = null
): ListPropertyEditor<ValueT, ModelListPropertyCore<ValueT>> {
  val boundProperty = property.bind(model)
  val boundContext = property.bindContext(model)
  return ListPropertyEditor(
    boundProperty, boundContext,
    { propertyCore, _, variables ->
      SimplePropertyEditor(propertyCore, boundContext, variables, createDefaultEditorExtensions(project, module))
    },
    variablesScope)
}

@Suppress("UNUSED_PARAMETER")
fun <ModelT, ValueT : Any, ModelPropertyT : ModelMapProperty<ModelT, ValueT>> mapPropertyEditor(
  project: PsProject,
  module: PsModule?,
  model: ModelT,
  property: ModelPropertyT,
  variablesScope: PsVariablesScope? = null
): MapPropertyEditor<ValueT, ModelMapPropertyCore<ValueT>> {
  val boundProperty = property.bind(model)
  val boundContext = property.bindContext(model)
  return MapPropertyEditor(
    boundProperty, boundContext,
    { propertyCore, _, variables ->
      SimplePropertyEditor(propertyCore, boundContext, variables, createDefaultEditorExtensions(project, module))
    },
    variablesScope)
}
