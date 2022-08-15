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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.intellij.openapi.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle
import java.util.Optional

// TODO(qumeric): search for "select.target.dialog.text", find usages and refactor
class ApiVersionValidator(
  parentDisposable: Disposable,
  private val formFactor: FormFactor,
  private val isAndroidXFun: () -> Boolean
): Validator<Optional<AndroidVersionsInfo.VersionItem>> {

  /**
   * We assume `true` until we get the actual answer to prevent a validation error. It would be better
   * to have a "tri-state" value ("computing", "true", "false") and make the validator smarter.
   */
  private var isAndroidX: Boolean = true

  init {
    // Compute isAndroidX on the IO dispatcher, and store the result in our [isAndroidX] field
    AndroidCoroutineScope(parentDisposable).launch(Dispatchers.IO) {
      isAndroidX = isAndroidXFun()
    }
  }
  override fun validate(value: Optional<AndroidVersionsInfo.VersionItem>): Validator.Result = when {
    !value.isPresent ->
      Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("select.target.dialog.text"))
    (value.get().minApiLevel >= AndroidVersion.VersionCodes.Q || formFactor === FormFactor.WEAR) && !isAndroidX ->
      Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("android.wizard.validate.module.needs.androidx"))
    else ->
      Validator.Result.OK
  }
}
