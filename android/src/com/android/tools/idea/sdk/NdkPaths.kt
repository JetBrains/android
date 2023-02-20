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
@file:JvmName("NdkPaths")
package com.android.tools.idea.sdk

import com.android.io.CancellableFileIo
import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.ui.validation.validators.PathValidator.Companion.forAndroidNdkLocation
import com.android.tools.sdk.SdkPaths
import java.io.File
import java.nio.file.Path

/** @see [validateAndroidNdk]
 */
@Deprecated("Deprecated", replaceWith = ReplaceWith("validateAndroidNdk(ndkPath, includePathInMessage)"))
fun validateAndroidNdk(ndkPath: File?, includePathInMessage: Boolean): SdkPaths.ValidationResult {
  return validateAndroidNdk(ndkPath?.toPath(), includePathInMessage)
}

/**
 * Indicates whether the given path belongs to a valid Android NDK.
 *
 * @param ndkPath              the given path.
 * @param includePathInMessage indicates whether the given path should be included in the result message.
 * @return the validation result.
 */
fun validateAndroidNdk(ndkPath: Path?, includePathInMessage: Boolean): SdkPaths.ValidationResult {
  if (ndkPath != null) {
    val (severity, message) = forAndroidNdkLocation().validate(ndkPath)
    if (severity === Validator.Severity.ERROR) {
      return SdkPaths.ValidationResult.error(message)
    }
  }
  val validationResult = SdkPaths.validatedSdkPath(ndkPath, "NDK", false, includePathInMessage)
  if (validationResult.success && ndkPath != null) {
    val toolchainsDirPath = ndkPath.resolve("toolchains")
    if (!CancellableFileIo.isDirectory(toolchainsDirPath)) {
      val message = if (includePathInMessage) {
        "The NDK at\n'${ndkPath}'\ndoes not contain any toolchains."
      }
      else {
        "NDK does not contain any toolchains."
      }
      return SdkPaths.ValidationResult.error(message)
    }
  }
  return validationResult
}