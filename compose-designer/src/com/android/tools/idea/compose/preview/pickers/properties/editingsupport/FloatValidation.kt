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
package com.android.tools.idea.compose.preview.pickers.properties.editingsupport

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.message

/**
 * [EditingValidation] instance that validates for positive (>0) float numbers.
 */
object FloatValidator : EditingValidation {
  override fun invoke(editedValue: String?): Pair<EditingErrorCategory, String> {
    if (editedValue == null || editedValue.isBlank()) return EDITOR_NO_ERROR
    val trimmedValue = editedValue.trim()

    val numberValue = trimmedValue.toFloatOrNull()
                      ?: return Pair(EditingErrorCategory.ERROR, message("picker.preview.input.validation.float.nan"))

    if (numberValue <= 0f) {
      return Pair(EditingErrorCategory.ERROR, message("picker.preview.input.validation.positive.value"))
    }

    if (!trimmedValue.isValidFormat()) {
      return Pair(EditingErrorCategory.WARNING, message("picker.preview.input.validation.float.format"))
    }

    return EDITOR_NO_ERROR
  }
}

/**
 * The user may type several strings that return a non-null value from [String.toFloatOrNull] but are not valid Float syntax, we issue
 * warnings for these identified cases since we don't control what the parser considers as a valid Float, or how they are parsed.
 *
 * Cases of Strings that can be parsed but are not correct syntax for Float:
 *
 * 1.
 *
 * 1.f
 *
 * 1d
 *
 * @return true if it follows supported Float syntax (1.0, 1.0f)
 */
private fun String.isValidFormat(): Boolean {
  if (!this.last().isDigit()) {
    // We handle the float suffix, so we consider no suffix as correct syntax, e.g: "1.1"
    val suffixOffset = this.indexOf('f')
    // In case there's a suffix, we just care that it's the correct Float suffix
    if (suffixOffset <= 0 || !this[suffixOffset - 1].isDigit()) {
      return false
    }
  }
  return true
}