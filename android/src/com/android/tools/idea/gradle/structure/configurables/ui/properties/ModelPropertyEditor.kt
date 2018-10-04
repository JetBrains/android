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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.Disposable
import javax.swing.Icon
import javax.swing.JComponent

/**
 * A model property editor.
 *
 * The editor wraps a component configured for editing of a specific property of type [ValueT].  The editor is bound to the property and the
 * property is automatically updated with the current value of the editor.
 */
interface ModelPropertyEditor<out ValueT : Any> : Disposable {
  /**
   * The component to be added to the model editor.
   */
  val component: JComponent

  /**
   * The component to be added to the model editor to represent the label of the property editor.
   */
  val labelComponent: JComponent

  /**
   * The component to be added to the model editor to represent the current status of the property editor.
   */
  val statusComponent: JComponent?

  /**
   * Returns the current value of the editor.
   */
  fun getValue(): Annotated<ParsedValue<ValueT>>

  /**
   * Attempts to update the bound property to the current value of the editor and returns the outcome of the action.
   */
  fun updateProperty(): UpdatePropertyOutcome

  /**
   * Reloads the editor from the bound property.
   */
  fun reload()

  /**
   * Reloads the editor from the bound property and keeps the value if it has been modified by the user.
   */
  fun reloadIfNotChanged() = reload()

  val property: ModelPropertyCore<out ValueT>
}

/**
 * The outcome of [ModelPropertyEditor.updateProperty].
 */
enum class UpdatePropertyOutcome {
  /**
   * The value of the editor hasn't changed.
   */
  NOT_CHANGED,
  /**
   * The property has been updated with the current value of the editor.
   */
  UPDATED,
  /**
   * The operation cannot be performed. The current value of the editor is invalid.
   */
  INVALID
}

/**
 * A descriptor of an additional property editor action provided as an editor extension.
 */
interface EditorExtensionAction<T : Any, ModelPropertyCoreT : ModelPropertyCore<T>> {
  val title: String
  val tooltip: String
  val icon: Icon
  fun invoke(property: ModelPropertyCoreT,
             editor: ModelPropertyEditor<T>,
             editorFactory: ModelPropertyEditorFactory<T, ModelPropertyCoreT>)
}

/**
 * A factory to create property editors with the preconfigured property context.
 */
interface ModelPropertyEditorFactory<ValueT : Any, in ModelPropertyCoreT : ModelPropertyCore<ValueT>> {
  fun createNew(property: ModelPropertyCoreT): ModelPropertyEditor<ValueT>
}
