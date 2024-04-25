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
package com.android.tools.property.panel.api

import com.android.tools.property.panel.impl.support.EditorProviderImpl
import com.android.tools.property.panel.impl.support.NameEditorProviderImpl
import javax.swing.JComponent

/** The context the editor will be used. */
enum class EditorContext {
  STAND_ALONE_EDITOR,
  TABLE_EDITOR,
  TABLE_RENDERER,
}

/**
 * Provider of an editor component with a corresponding model for a property.
 *
 * A client can either provide a custom editor provider or use the default implementation by calling
 * [EditorProvider.create].
 *
 * @param P a client defined property class
 */
interface EditorProvider<in P : PropertyItem> {

  /**
   * Create an editor for a [property] optionally [asTableCellEditor].
   *
   * The editor created may be different if it is supposed to be used as a cell editor in a table.
   * We may choose to either hide or make the border different inside a table.
   */
  fun createEditor(
    property: P,
    context: EditorContext = EditorContext.STAND_ALONE_EDITOR,
  ): Pair<PropertyEditorModel, JComponent>

  companion object {
    /**
     * Create a default [EditorProvider] for editing property values.
     *
     * @param enumSupportProvider must be specified for creating [EnumSupport] for a given property
     *   [P].
     * @param controlTypeProvider must be specified for determining the [ControlType] for the given
     *   property [P].
     */
    fun <P : PropertyItem> create(
      enumSupportProvider: EnumSupportProvider<P>,
      controlTypeProvider: ControlTypeProvider<P>,
    ): EditorProvider<P> {
      return EditorProviderImpl(enumSupportProvider, controlTypeProvider)
    }

    /** Create a default [EditorProvider] for editing property names. */
    fun <P : NewPropertyItem> createForNames(): EditorProvider<P> {
      return NameEditorProviderImpl()
    }
  }
}

/**
 * Provider of a [EnumSupport] for a property.
 *
 * Some properties may be best edited in a ComboBox or a DropDown. There is builtin support for this
 * by supplying an [EnumSupport] for those properties.
 *
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 */
interface EnumSupportProvider<in P : PropertyItem> : (P) -> EnumSupport?

/**
 * Provider of a [ControlType] for given property.
 *
 * The default [EditorProvider] uses a [ControlTypeProvider] to determine the [ControlType] for
 * properties. For a custom implementation of [EditorProvider] this interface may not be required.
 *
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 */
interface ControlTypeProvider<in P : PropertyItem> : (P) -> ControlType
