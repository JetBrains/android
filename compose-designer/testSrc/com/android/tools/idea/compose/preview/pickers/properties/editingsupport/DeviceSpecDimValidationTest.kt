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
package com.android.tools.idea.compose.preview.pickers.properties.editingsupport

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingValidation
import kotlin.test.assertEquals
import org.junit.Test

class DeviceSpecDimValidationTest {
  lateinit var validator: EditingValidation

  @Test
  fun testNormalValidator() {
    validator = DeviceSpecDimValidator(strictPositive = false)

    assertEquals(EDITOR_NO_ERROR, validator("10"))
    assertEquals(EDITOR_NO_ERROR, validator("20.0"))
    assertEquals(EDITOR_NO_ERROR, validator("   30.0f   "))
    assertEquals(EDITOR_NO_ERROR, validator("0.4"))
    assertEquals(EDITOR_NO_ERROR, validator("0.5"))

    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-10"))
    assertEquals(ERROR_GREATER_THAN_ZERO, validator("-20"))
    assertEquals(ERROR_NOT_FLOAT, validator("abc"))

    assertEquals(WARN_DECIMALS, validator("10.01f"))
  }

  @Test
  fun testStrictPositiveValidator() {
    validator = DeviceSpecDimValidator(strictPositive = true)

    assertEquals(EDITOR_NO_ERROR, validator("10"))
    assertEquals(EDITOR_NO_ERROR, validator("20.0"))
    assertEquals(EDITOR_NO_ERROR, validator("   30.0f   "))
    assertEquals(EDITOR_NO_ERROR, validator("0.5"))

    assertEquals(ERROR_GREATER_THAN_ZERO, validator("0.4"))
    assertEquals(ERROR_NOT_FLOAT, validator("abc"))

    assertEquals(WARN_DECIMALS, validator("10.01f"))
  }
}
