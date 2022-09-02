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
import com.android.tools.adtui.model.stdui.EditingValidation
import kotlin.test.assertEquals
import org.junit.Test

class FloatValidationTest {

  lateinit var validator: EditingValidation

  @Test
  fun testValidator() {
    validator = FloatValidator
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
}
