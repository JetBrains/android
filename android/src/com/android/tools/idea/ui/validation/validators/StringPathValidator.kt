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
import java.nio.file.InvalidPathException
import java.nio.file.Paths

class StringPathValidator(private val pathValidator: PathValidator) : Validator<String> {

  override fun validate(value: String): Validator.Result {
    val path = try {
      Paths.get(value)
    }
    catch (e: InvalidPathException) {
      return Validator.Result(Validator.Severity.ERROR, "${pathValidator.pathName} in not a valid file system path")
    }
    return pathValidator.validate(path)
  }
}