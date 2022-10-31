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
import kotlin.test.assertEquals
import org.junit.Test

class FloatValidationTest {

  val validation = FloatValidation

  @Test
  fun editedValueValid() {
    assertEquals(EDITOR_NO_ERROR, validation("1"))
    assertEquals(EDITOR_NO_ERROR, validation("1.0"))
    assertEquals(EDITOR_NO_ERROR, validation("1."))
    assertEquals(EDITOR_NO_ERROR, validation("1f"))
    assertEquals(EDITOR_NO_ERROR, validation("1.123"))
    assertEquals(EDITOR_NO_ERROR, validation("     1.123     "))
    assertEquals(EDITOR_NO_ERROR, validation("-1.123"))
    assertEquals(EDITOR_NO_ERROR, validation(""))
  }

  @Test
  fun editedValueInvalid() {
    assertEquals(EditingErrorCategory.ERROR, validation("one").first)
    assertEquals(EditingErrorCategory.ERROR, validation("1.0p").first)
    assertEquals(EditingErrorCategory.ERROR, validation("1    .0").first)
    assertEquals(EditingErrorCategory.ERROR, validation("1.p").first)
    assertEquals(EditingErrorCategory.ERROR, validation("1p").first)
    assertEquals(EditingErrorCategory.ERROR, validation("1.123p").first)
  }
}
