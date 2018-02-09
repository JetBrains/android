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
package com.android.tools.idea.common.property2.api

import com.android.tools.idea.common.property2.impl.support.EditorProviderImpl
import javax.swing.JComponent

/**
 * Provider of an editor component with a corresponding model for a property.
 *
 * A client can either provide a custom editor provider or use the default
 * implementation by calling [EditorProvider.create].
 * @param P a client defined property class
 */
interface EditorProvider<in P: PropertyItem> {
  fun provide(property: P): Pair<PropertyEditorModel, JComponent>

  companion object {
    /**
     * Create a default [EditorProvider].
     *
     * @param enumSupportProvider must be specified for creating [EnumSupport] for a given property [P].
     * @param controlTypeProvider must be specified for determining the [ControlType] for the given property [P].
     * @param formModel is a higher level model which is used in the default editor implementations.
     */
    fun <P : PropertyItem> create(
      enumSupportProvider: EnumSupportProvider<P>,
      controlTypeProvider: ControlTypeProvider<P>,
      formModel: FormModel
    ): EditorProvider<P> {
      return EditorProviderImpl(enumSupportProvider, controlTypeProvider, formModel)
    }
  }
}

/**
 * Provider of a [EnumSupport] for a property.
 *
 * Some properties may be best edited in a ComboBox or a DropDown.
 * There is builtin support for this by supplying an [EnumSupport]
 * for those properties.
 *
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 */
typealias EnumSupportProvider<P> = (P) -> EnumSupport?

/**
 * Provider of a [ControlType] for given property.
 *
 * The default [EditorProvider] uses a [ControlTypeProvider] to determine the
 * [ControlType] for properties. For a custom implementation of [EditorProvider]
 * this interface may not be required.
 *
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 */
typealias ControlTypeProvider<P> = (P, EnumSupport?) -> ControlType
