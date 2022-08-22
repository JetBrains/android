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

private const val HEX_PREFIX = "0x"

/**
 * [EditingValidation] instance to validate string inputs to use as color values in kotlin code.
 *
 * The input should be a hexadecimal literal, the typical [HEX_PREFIX] is optional.
 */
internal object ColorValidation : EditingValidation {
  override fun invoke(editedValue: String?): Pair<EditingErrorCategory, String> {
    if (editedValue.isNullOrBlank()) return EDITOR_NO_ERROR
    val trimmedValue = editedValue.trim()
    val colorHexValue = trimmedValue.replace(HEX_PREFIX, "", ignoreCase = true)

    if (trimmedValue.contains("0x", ignoreCase = true) &&
        !trimmedValue.startsWith("0x", ignoreCase = true)
    ) {
      return Pair(
        EditingErrorCategory.ERROR,
        message("picker.preview.input.validation.color.format")
      )
    }

    val colorValue =
      colorHexValue.toLongOrNull(16)
        ?: return Pair(
          EditingErrorCategory.ERROR,
          message("picker.preview.input.validation.color.format")
        )
    if (colorValue > 0xFFFFFFFFL) {
      return Pair(EditingErrorCategory.ERROR, message("picker.preview.input.validation.color.max"))
    }
    if (colorValue < 0) {
      return Pair(
        EditingErrorCategory.ERROR,
        message("picker.preview.input.validation.color.negative")
      )
    }

    // Warnings should be checked after all possible errors.
    if (colorHexValue.length <= 6) {
      return Pair(
        EditingErrorCategory.WARNING,
        message("picker.preview.input.validation.color.alpha")
      )
    }
    return EDITOR_NO_ERROR
  }
}
