/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.ui.validation.validators

import com.android.tools.adtui.validation.Validator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringPathValidatorTest {

  private val validator = StringPathValidator(PathValidator.createDefault("Test path"))

  @Test
  fun testValid() {
    val result = validator.validate("${System.getProperty("java.io.tmpdir")}/valid/path")
    assertThat(result.severity).isEqualTo(Validator.Severity.OK)
  }

  @Test
  fun testInvalid() {
    val result = validator.validate("${System.getProperty("java.io.tmpdir")}/path/with/illegal/character\u0000")
    assertThat(result.severity).isEqualTo(Validator.Severity.ERROR)
    assertThat(result.message).isEqualTo("Test path in not a valid file system path")
  }
}