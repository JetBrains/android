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
package com.android.tools.idea.compose.pickers.base.enumsupport

import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiEnumValueCellRenderer
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.NewEnumValueCallback
import com.android.tools.property.panel.api.PropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent

/** Base interface for psi pickers, to support tracking assigned values. */
internal interface PsiEnumValue : EnumValue {
  val trackableValue:
    EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue

  override fun select(property: PropertyItem, newEnumValue: NewEnumValueCallback): Boolean =
    if (property is PsiCallParameterPropertyItem) {
      newEnumValue.newValue(value)
      property.writeNewValue(value, false, trackableValue)
      true
    } else {
      super.select(property, newEnumValue)
    }

  companion object {
    fun withTooltip(
      value: String,
      display: String,
      description: String?,
      trackingValue:
        EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
    ) = DescriptionEnumValue(value, display, trackingValue, description)

    fun indented(
      value: String,
      display: String,
      trackingValue:
        EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
    ) =
      object : PsiEnumValueImpl(value = value, display = display, trackableValue = trackingValue) {
        override val indented: Boolean = true
      }
  }
}

/**
 * Base implementation of [PsiEnumValue], should aim to cover most use-cases found in [EnumValue].
 */
internal open class PsiEnumValueImpl(
  override val value: String?,
  override val display: String,
  override val trackableValue:
    EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
) : PsiEnumValue

/** [PsiEnumValue] that includes a description, shown as a tooltip in [PsiEnumValueCellRenderer]. */
internal data class DescriptionEnumValue(
  override val value: String,
  override val display: String,
  override val trackableValue:
    EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
  val description: String?,
) : PsiEnumValue {
  override val indented: Boolean = true

  override fun toString(): String = value
}
