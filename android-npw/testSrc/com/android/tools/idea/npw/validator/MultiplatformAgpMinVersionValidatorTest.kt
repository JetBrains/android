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
package com.android.tools.idea.npw.validator

import com.android.ide.common.repository.AgpVersion
import com.android.tools.adtui.validation.Validator
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MultiplatformAgpMinVersionValidatorTest {
  private lateinit var moduleValidator: MultiplatformAgpMinVersionValidator

  @Before
  fun createModuleValidator() {
    moduleValidator = MultiplatformAgpMinVersionValidator()
  }

  @Test
  fun testValidAgpVersion() {
    assertValidVersion(AgpVersion.parse("8.8.0-beta02"))
    assertValidVersion(AgpVersion.parse("8.8.0-rc01"))
    assertValidVersion(AgpVersion.parse("8.8.0"))
    assertValidVersion(AgpVersion.parse("8.8.1"))
    assertValidVersion(AgpVersion.parse("8.9.0"))
  }

  @Test
  fun testInvalidAgpVersion() {
    assertInvalidVersion(AgpVersion.parse("8.8.0-beta01"))
    assertInvalidVersion(AgpVersion.parse("8.8.0-alpha08"))
    assertInvalidVersion(AgpVersion.parse("8.7.0"))
  }

  private fun assertValidVersion(version: AgpVersion) {
    val result = moduleValidator.validate(version)
    Assert.assertSame(Validator.Severity.OK, result.severity)
  }

  private fun assertInvalidVersion(version: AgpVersion) {
    val result = moduleValidator.validate(version)
    Assert.assertSame(result.message, Validator.Severity.ERROR, result.severity)
    Assert.assertEquals(
      "Android Gradle Plugin version should be higher than or equal to 8.8.0-beta02",
      result.message,
    )
  }
}
