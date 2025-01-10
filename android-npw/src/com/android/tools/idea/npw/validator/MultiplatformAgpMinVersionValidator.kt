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
import com.android.tools.idea.npw.model.AgpVersionSelector
import org.jetbrains.android.util.AndroidBundle.message

/** Validates that agp version is high enough for kotlin multiplatform module creation */
class MultiplatformAgpMinVersionValidator : Validator<AgpVersionSelector> {
  override fun validate(agpVersionSelector: AgpVersionSelector): Validator.Result {
    if (!agpVersionSelector.willSelectAtLeast(MINIMUM_SUPPORTED_AGP_VERSION)) {
      return Validator.Result(
        Validator.Severity.ERROR,
        message("android.wizard.validate.agp.version.for.kmp.module", MINIMUM_SUPPORTED_AGP_VERSION),
      )
    }

    return Validator.Result.OK
  }

  companion object {
    private val MINIMUM_SUPPORTED_AGP_VERSION = AgpVersion.parse("8.8.0-beta02")
  }
}
