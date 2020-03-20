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
import com.intellij.openapi.module.Module
import org.jetbrains.android.util.AndroidBundle
import java.util.Optional

class ModuleSelectedValidator: Validator<Optional<Module>> {
  override fun validate(value: Optional<Module>): Validator.Result =
    if (value.isPresent)
      Validator.Result.OK
    else
      Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("android.wizard.module.new.dynamic.select.base"))
}
