/*
 * Copyright (C) 2017 The Android Open Source Project
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
import org.jetbrains.android.util.AndroidUtils

/**
 * Validates a Java class name
 */
class ClassNameValidator : Validator<String> {
  override fun validate(value: String): Result =
    if (value.isEmpty() || '.' in value || !AndroidUtils.isIdentifier(value))
      Result(Severity.ERROR, message("android.wizard.validate.invalid.class.name"))
    else
      Result.OK
}
