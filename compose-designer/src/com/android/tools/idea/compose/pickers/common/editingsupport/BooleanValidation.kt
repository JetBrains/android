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

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.message

/** Validates for three state Boolean. */
val BooleanValidator = createBooleanValidator()

/** Creates a validator for a three state Boolean. */
private fun createBooleanValidator(): EditingValidation =
  validator@{ editedValue: String? ->
    if (editedValue == null || editedValue.isBlank()) return@validator EDITOR_NO_ERROR

    when (editedValue.trim()) {
      SdkConstants.VALUE_TRUE, SdkConstants.VALUE_FALSE -> return@validator EDITOR_NO_ERROR
      else ->
        return@validator Pair(
          EditingErrorCategory.ERROR,
          message("picker.preview.input.validation.boolean.nan")
        )
    }
  }
