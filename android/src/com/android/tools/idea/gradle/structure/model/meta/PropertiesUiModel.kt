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

import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.VariablesProvider

/**
 * A model of the UI for editing the properties of a model of type [ModelT].
 *
 * This is the basic UI model defining a set of properties to be edited and their order.
 */
class PropertiesUiModel<in ModelT>(val properties: List<PropertyUiModel<ModelT, *>>)

/**
 * A UI model of a property of type [PropertyT] of a model of type [ModelT].
 */
interface PropertyUiModel<in ModelT, out PropertyT> {
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
  PropertyEditorFactory<ModelPropertyCoreT, ModelPropertyContextT, PropertyT> =
  (ModelPropertyCoreT, ModelPropertyContextT, VariablesProvider?) -> ModelPropertyEditor<PropertyT>

/**
 * Creates a UI property model describing how to represent [property] for editing.
 *
 * @param editorFactory the function to create an editor bound to [property]
 */
fun <ModelT, PropertyT : Any, ValueT : Any, ModelPropertyCoreT,
  ModelPropertyT : ModelProperty<Nothing?, ModelT, PropertyT, ValueT, ModelPropertyCoreT>> uiProperty(
  property: ModelPropertyT,
  editorFactory: PropertyEditorFactory<ModelPropertyCoreT, ModelPropertyContext<ValueT>, PropertyT>
): PropertyUiModel<ModelT, *> =
  PropertyUiModelImpl(property, editorFactory, null)

class PropertyUiModelImpl<in ContextT, in ModelT, PropertyT : Any, ValueT : Any,
  out ModelPropertyCoreT, out ModelPropertyT : ModelProperty<ContextT, ModelT, PropertyT, ValueT, ModelPropertyCoreT>>(
  private val property: ModelPropertyT,
  private val editorFactory: PropertyEditorFactory<ModelPropertyCoreT, ModelPropertyContext<ValueT>, PropertyT>,
  private val context: ContextT
) : PropertyUiModel<ModelT, PropertyT> {
  override val propertyDescription: String = property.description
  override fun createEditor(project: PsProject, module: PsModule, model: ModelT)
    : ModelPropertyEditor<PropertyT> {
    val boundProperty = property.bind(model)
    val boundContext = property.bindContext(context, model)
    return editorFactory(boundProperty, boundContext, module.variables)
  }
}
