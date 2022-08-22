/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.enumsupport

import com.android.tools.idea.compose.pickers.common.property.ClassPsiCallParameter
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue

/**
 * Base interface that makes use of [ClassPsiCallParameter] functionality.
 *
 * Used to import classes and set parameter values that may use references to the imported class.
 */
internal interface BaseClassEnumValue : EnumValue {
  /** The fully qualified class that needs importing */
  val fqClass: String

  /** The new value String of the parameter */
  val valueToWrite: String

  /** Value to use in case the [fqClass] cannot be imported */
  val fqFallbackValue: String

  /**
   * Resolved primitive value for this [EnumValue], used for comparing with other references that
   * may lead to the same value
   */
  val resolvedValue: String

  /**
   * One of the supported tracking options that best represents the value assigned by this instance,
   * use [PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED] if there's no suitable option.
   */
  val trackableValue: PreviewPickerValue

  override val value: String?
    get() = resolvedValue

  override fun select(property: PropertyItem): Boolean {
    if (property is ClassPsiCallParameter) {
      property.importAndSetValue(fqClass, valueToWrite, fqFallbackValue, trackableValue)
    } else {
      property.value = fqFallbackValue
    }
    return true
  }
}
