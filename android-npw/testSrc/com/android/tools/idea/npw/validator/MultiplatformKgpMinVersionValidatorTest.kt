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

import com.android.tools.adtui.validation.Validator
import java.util.Optional
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MultiplatformKgpMinVersionValidatorTest {
  private lateinit var moduleValidator: MultiplatformKgpMinVersionValidator

  @Before
  fun createModuleValidator() {
    moduleValidator = MultiplatformKgpMinVersionValidator()
  }

  @Test
  fun testValidKgpVersion() {
    assertValidVersion(Optional.of(KotlinGradlePluginVersion.parse("1.9.20")!!))
    assertValidVersion(Optional.of(KotlinGradlePluginVersion.parse("2.0.0")!!))
    assertValidVersion(Optional.of(KotlinGradlePluginVersion.parse("2.0.0-Beta")!!))
    assertValidVersion(Optional.of(KotlinGradlePluginVersion.parse("2.0.0-Beta2")!!))
    assertValidVersion(Optional.of(KotlinGradlePluginVersion.parse("2.0.0-RC")!!))
  }

  @Test
  fun testInvalidKgpVersion() {
    assertInvalidVersion(Optional.of(KotlinGradlePluginVersion.parse("1.9.10")!!))
    assertInvalidVersion(Optional.of(KotlinGradlePluginVersion.parse("1.9.0")!!))
    assertInvalidVersion(Optional.of(KotlinGradlePluginVersion.parse("1.8.20")!!))
    assertInvalidVersion(Optional.ofNullable(null))
  }

  private fun assertValidVersion(version: Optional<KotlinGradlePluginVersion>) {
    val result = moduleValidator.validate(version)
    Assert.assertSame(Validator.Severity.OK, result.severity)
  }

  private fun assertInvalidVersion(version: Optional<KotlinGradlePluginVersion>) {
    val result = moduleValidator.validate(version)
    Assert.assertSame(result.message, Validator.Severity.ERROR, result.severity)
    Assert.assertEquals(
      "Kotlin Gradle Plugin version should be higher than or equal to 1.9.20",
      result.message,
    )
  }
}
