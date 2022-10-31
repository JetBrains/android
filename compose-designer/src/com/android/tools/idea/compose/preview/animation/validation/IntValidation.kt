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
package com.android.tools.idea.compose.preview.animation.validation

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.message

/** [EditingValidation] instance that validates for int numbers. */
object IntValidation : EditingValidation {
  /**
   * Validates that [editedValue] corresponds to a valid Float number.
   *
   * @return An [EditingErrorCategory] representing the validation result with its corresponding
   * display message [String].
   */
  override fun invoke(editedValue: String?): Pair<EditingErrorCategory, String> {
    return when {
      editedValue.isNullOrBlank() -> EDITOR_NO_ERROR
      editedValue.trim().toIntOrNull() == null ->
        return Pair(
          EditingErrorCategory.ERROR,
          message("animation.inspector.picker.validation.integer.nan")
        )
      else -> EDITOR_NO_ERROR
    }
  }
}
