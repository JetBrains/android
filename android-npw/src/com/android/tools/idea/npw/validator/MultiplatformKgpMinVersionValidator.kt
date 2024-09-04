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

import com.android.ide.common.gradle.Version
import com.android.tools.adtui.validation.Validator
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Validates that kgp version is high enough for kotlin multiplatform module creation
 */
class MultiplatformKgpMinVersionValidator: Validator<String> {
  override fun validate(value: String): Validator.Result {
    val currentKgpVersion = Version.parse(value)
    if (currentKgpVersion < MINIMUM_SUPPORTED_KOTLIN_MULTIPLATFORM_VERSION) {
      return Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.kgp.version.for.kmp.module"))
    }

    return Validator.Result.OK
  }

  companion object {
    private val MINIMUM_SUPPORTED_KOTLIN_MULTIPLATFORM_VERSION = Version.parse("1.9.20-Beta")
  }
}