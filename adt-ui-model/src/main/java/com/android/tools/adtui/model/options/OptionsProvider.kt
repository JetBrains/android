/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.model.options

import java.lang.reflect.Method
import javax.swing.JComponent

/**
 * This interface defines an object that supplies accessor/mutator methods via the {@link Property} attribute. The interface is used
 * as the "this" object when resolving accessor / mutator calls. Eg accessor.invoke(optionsProvider).
 */
interface OptionsProvider

/**
 * Data class that is used to store metadata related to each {@link Property} attribute. This class is responsible for managing the
 * accessor/mutator functions as well as setting / getting values from the {@link OptionsProvider}.
 */
data class PropertyInfo(val provider: OptionsProvider, val methodName: String) {
  /**
   * Name of the property. If this is not set a stripped version of the method name is used.
   * see {@link OptionsPanel::cleanMethodName}.
   */
  var name = methodName

  /**
   * Description of the property to be displayed in the UI.
   */
  var description: String = ""

  /**
   * Optional unit string to be displayed after the input control. Eg Sample Rate: [   0] Ms (Milliseconds).
   * If no unit is provided no control should be added. This is a determined by the {@link OptionsBinder}.
   */
  var unit: String = ""

  /**
   * Group used to bucket like properties together. The {@link DEFAULT_GROUP} does not create a grouping instead acts as an empty group.
   * Eg For two groups ("Trace", "Other") the UI may look like the following.
   * Trace -------------
   * Name:        [        ]
   * Sample Rate: [     100]
   * Other ----------
   *  [x] live allocation tracking
   */
  var group: String = DEFAULT_GROUP

  /**
   * Method used to get the value of this property. The return value must match the set value of the mutator.
   */
  var accessor: Method? = null

  /**
   * Method used to set the value of this property. Only one parameter is expected and the type must match the return value of the accessor.
   */
  var mutator: Method? = null

  /**
   * Binder used to build UI component for this property.
   */
  var binder: OptionsBinder? = null

  /**
   * Order used by layout to determine placement.
   */
  var order: Int = DEFAULT_ORDER

  /**
   * Helper property to get/set value on {@link OptionsProvider}
   */
  var value: Any?
    get() {
      return accessor?.invoke(provider)
    }
    set(value) {
      mutator?.invoke(provider, value)
    }
}

/**
 * OptionsBinders are used to convert from a propety type to JComponent.
 * Eg: property [int getMyVal()] will use an Int binder by default. Binders can be overridden or added to each {@link OptionsPanel}
 */
interface OptionsBinder {
  fun bind(data: PropertyInfo, readonly: Boolean = false): JComponent
}