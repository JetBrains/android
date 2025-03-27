/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceNameValidatorTest {

  @Test
  fun uniquify() {
    val validator = DeviceNameValidator(setOf("Pixel", "Pixel 2", "Pixel (2)"))

    assertThat(validator.uniquify("Pixel")).isEqualTo("Pixel (3)")
    assertThat(validator.uniquify("Pixel 2")).isEqualTo("Pixel 2 (2)")
    assertThat(validator.uniquify("Pixel (2)")).isEqualTo("Pixel (3)")
  }

  @Test
  fun validate() {
    val validator = DeviceNameValidator(setOf("Pixel", "Pixel 2", "Pixel (2)"), "Pixel (2)")

    assertThat(validator.validate("Pixel 2")).contains("already exists")
    assertThat(validator.validate("Pixel ")).contains("already exists")
    assertThat(validator.validate("Pixel!")).contains("can contain only")
    assertThat(validator.validate(" ")).isEqualTo("The name cannot be blank.")
    assertThat(validator.validate("Pixel (2)")).isNull()
  }
}
