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
import com.android.tools.adtui.model.stdui.EditingValidation
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatValidationTest {

  @Test
  fun testValidator() {
    val validator: EditingValidation = FloatValidator
    assertEquals(EDITOR_NO_ERROR, validator(""))
    assertEquals(EDITOR_NO_ERROR, validator("   "))
    assertEquals(EDITOR_NO_ERROR, validator("1.0f"))
    assertEquals(EDITOR_NO_ERROR, validator("1.0"))
    assertEquals(EDITOR_NO_ERROR, validator("1f"))
    assertEquals(EDITOR_NO_ERROR, validator("1"))

    assertEquals(ERROR_NOT_FLOAT, validator("Test"))
    assertEquals(ERROR_NOT_FLOAT, validator("1.0 f"))

    assertEquals(ERROR_GREATER_THAN_ZERO, validator("0"))
    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-1"))
    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-1.0"))
    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-1.0f"))
    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-1."))

    assertEquals(WARN_FORMAT, validator("1."))
    assertEquals(WARN_FORMAT, validator("1.f"))
    assertEquals(WARN_FORMAT, validator("1.0d"))
  }

  @Test
  fun testFunctionValidateFloat() {
    // We don't need to test the case of validateSuffix = true because it is tested already in
    // FloatValidator
    assertEquals(
      EDITOR_NO_ERROR,
      validateFloat(editedValue = "0", validateSuffix = false, canBeZero = true),
    )
    assertEquals(
      EDITOR_NO_ERROR,
      validateFloat(editedValue = "0f", validateSuffix = false, canBeZero = true),
    )
    assertEquals(
      EDITOR_NO_ERROR,
      validateFloat(
        editedValue = "500",
        validateSuffix = false,
        canBeZero = true,
        maxValueAllowed = 500f,
      ),
    )
    assertEquals(
      EDITOR_NO_ERROR,
      validateFloat(
        editedValue = "400",
        validateSuffix = false,
        canBeZero = true,
        maxValueAllowed = 500f,
      ),
    )
    assertEquals(
      ERROR_NOT_FLOAT,
      validateFloat(editedValue = "abc", validateSuffix = false, canBeZero = true),
    )
    assertEquals(
      ERROR_GREATER_THAN_ZERO,
      validateFloat(
        editedValue = "-1",
        validateSuffix = false,
        canBeZero = false,
        maxValueAllowed = 500f,
      ),
    )
    assertEquals(
      ERROR_GREATER_THAN_ZERO,
      validateFloat(
        editedValue = "0",
        validateSuffix = false,
        canBeZero = false,
        maxValueAllowed = 500f,
      ),
    )
    val maxValue = 10f
    assertEquals(
      ERROR_TOO_BIG(maxValue.toInt()),
      validateFloat(
        editedValue = "400",
        validateSuffix = false,
        canBeZero = true,
        maxValueAllowed = 10f,
      ),
    )
  }
}
