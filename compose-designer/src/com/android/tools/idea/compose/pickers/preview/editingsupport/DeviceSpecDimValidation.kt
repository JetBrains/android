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
package com.android.tools.idea.compose.pickers.preview.editingsupport

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.pickers.common.editingsupport.validateFloat

/**
 * [EditingValidation] for Dimension parameters of the DeviceSpec.
 *
 * A dimension parameter value is a float number that may have up to one decimal.
 *
 * @param strictPositive If true, [EditingErrorCategory.ERROR] will be used when the value is
 * effectively zero (<0.5f).
 */
internal class DeviceSpecDimValidator(private val strictPositive: Boolean) : EditingValidation {
  override fun invoke(editedValue: String?): Pair<EditingErrorCategory, String> {
    if (editedValue == null || editedValue.isBlank()) return EDITOR_NO_ERROR
    val trimmedValue = editedValue.trim()

    val validFloatResult =
      validateFloat(editedValue = trimmedValue, validateSuffix = false, canBeZero = !strictPositive)
    if (validFloatResult != EDITOR_NO_ERROR) {
      return validFloatResult
    }

    return validateDecimals(trimmedValue)
  }
}

private fun validateDecimals(dimensionValue: String): Pair<EditingErrorCategory, String> {
  val dimValueWithNoUnit = dimensionValue.dropLastWhile { it == 'f' }

  val decimalIndex = dimValueWithNoUnit.indexOfLast { it == '.' }
  if (decimalIndex >= 0 && (dimValueWithNoUnit.length - 1) - decimalIndex > 1) {
    return Pair(EditingErrorCategory.WARNING, "Only one decimal supported")
  }

  return EDITOR_NO_ERROR
}
