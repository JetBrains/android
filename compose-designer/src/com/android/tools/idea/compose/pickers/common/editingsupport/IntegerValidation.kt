/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.editingsupport

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.message

/** Validates for non-negative integers, but shows a warning for 'zero' values. */
val IntegerNormalValidator = createIntegerValidator(IntegerValidatorType.NORMAL)

/** Validates for positive (>0) integers. */
val IntegerStrictValidator = createIntegerValidator(IntegerValidatorType.STRICT)

/** Creates an [EditingValidation] instance that validates for positive (>0) integer numbers. */
private fun createIntegerValidator(type: IntegerValidatorType): EditingValidation =
  validator@{ editedValue: String? ->
    if (editedValue.isNullOrBlank()) return@validator EDITOR_NO_ERROR
    val trimmedValue = editedValue.trim()

    val numberValue =
      if (trimmedValue.isHex()) {
        trimmedValue.replace("0x", "", true).toIntOrNull(16)
      } else {
        trimmedValue.toIntOrNull()
      }
        ?: return@validator Pair(
          EditingErrorCategory.ERROR,
          message("picker.preview.input.validation.integer.nan")
        )

    if (numberValue < 0) {
      return@validator Pair(
        EditingErrorCategory.ERROR,
        message("picker.preview.input.validation.positive.value")
      )
    }

    if (numberValue == 0) {
      return@validator Pair(
        when (type) {
          IntegerValidatorType.NORMAL -> EditingErrorCategory.WARNING
          IntegerValidatorType.STRICT -> EditingErrorCategory.ERROR
        },
        message("picker.preview.input.validation.positive.value")
      )
    }
    EDITOR_NO_ERROR
  }

private fun String.isHex() = contains("0x", true) // First character may be a sign (-) character

private enum class IntegerValidatorType {
  /** To validate for non-negative integers, while issuing a warning for zero. */
  NORMAL,

  /** To validate for positive integers (>0). */
  STRICT
}
