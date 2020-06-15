/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.adtui.validation.Validator.Result
import com.android.tools.adtui.validation.Validator.Severity
import org.jetbrains.android.util.AndroidBundle.message

private const val bannedSymbols = "/\\:<>\"?*|"

/**
 * Validates a project name.
 */
class ProjectNameValidator : Validator<String> {
  override fun validate(value: String): Result {
    val firstIllegalSymbolIx = value.indexOfFirst { it in bannedSymbols }
    return when {
      value.isEmpty() -> Result(Severity.ERROR, message("android.wizard.validate.empty.application.name"))
      firstIllegalSymbolIx >= 0 ->
        Result(Severity.ERROR, message("android.wizard.validate.project.illegal.character", value[firstIllegalSymbolIx], value))
      !Character.isUpperCase(value[0]) -> Result(Severity.INFO, message("android.wizard.validate.lowercase.application.name"))
      else -> Result.OK
    }
  }
}
