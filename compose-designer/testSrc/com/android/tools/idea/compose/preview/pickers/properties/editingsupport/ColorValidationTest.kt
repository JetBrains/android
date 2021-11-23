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

import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.google.common.primitives.UnsignedInteger
import org.junit.Test
import kotlin.test.assertEquals

internal class ColorValidationTest {

  val validator = ColorValidation

  @Test
  fun validInputs() {
    assertEquals(EditingErrorCategory.NONE, validator("FF0011AA").first)
    assertEquals(EditingErrorCategory.NONE, validator("ff0011aa").first)
    assertEquals(EditingErrorCategory.NONE, validator("0xFF0011AA").first)
    assertEquals(EditingErrorCategory.NONE, validator("0XFF0011AA").first)
    assertEquals(EditingErrorCategory.NONE, validator("   0xFF0011AA   ").first)
    assertEquals(EditingErrorCategory.NONE, validator("00FF0011AA").first)
    assertEquals(EditingErrorCategory.NONE, validator("0x00FF0011AA").first)
  }

  @Test
  fun alphaWarning() {
    val missingAlphaOutput = Pair(EditingErrorCategory.WARNING, "Missing value for alpha channel")
    assertEquals(missingAlphaOutput, validator("0xAABBCC"))
    assertEquals(missingAlphaOutput, validator("AABBCC"))
  }

  @Test
  fun badFormat() {
    val errorAndMessage = Pair(EditingErrorCategory.ERROR, "Color should be an aRGB hex literal (0xAARRGGBB)")
    assertEquals(errorAndMessage, validator("Hello, world!"))
    assertEquals(errorAndMessage, validator("FFAABBGG"))
    assertEquals(errorAndMessage, validator("FFAA0011p"))
    assertEquals(errorAndMessage, validator("AA0xFF0011AA"))
    assertEquals(errorAndMessage, validator("AA0x0011AA"))
    assertEquals(errorAndMessage, validator("   00xFF0011AA   "))
  }

  @Test
  fun valueBounds() {
    // The max color possible (0xFFFFFFFF) corresponds to the maximum unsigned integer value
    assertEquals(EditingErrorCategory.NONE, validator((UnsignedInteger.MAX_VALUE).toString(16)).first)

    assertEquals(
      Pair(EditingErrorCategory.ERROR, "Value can't be higher than 0xFFFFFFFF"),
      validator((UnsignedInteger.MAX_VALUE.toLong() + 1).toString(16))
    )
    assertEquals(
      Pair(EditingErrorCategory.ERROR, "Value can't be higher than 0xFFFFFFFF"),
      validator(Long.MAX_VALUE.toString(16))
    )
    assertEquals(
      Pair(EditingErrorCategory.ERROR, "Color can't be a negative value"),
      validator("-1")
    )
  }
}