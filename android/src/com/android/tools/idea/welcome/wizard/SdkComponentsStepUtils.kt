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
package com.android.tools.idea.welcome.wizard

import com.android.io.CancellableFileIo
import com.android.tools.sdk.AndroidSdkData
import com.android.tools.sdk.isValid
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.containers.isEmpty
import com.intellij.util.containers.notNullize
import java.io.File
import org.jetbrains.annotations.Contract

internal object SdkComponentsStepUtils {

  @Contract("null->null")
  @JvmStatic
  fun getExistingParentFile(path: String?): File? {
    if (path.isNullOrEmpty()) {
      return null
    }

    return generateSequence(File(path).absoluteFile) { it.parentFile }.firstOrNull(File::exists)
  }

  @JvmStatic
  fun getDiskSpace(path: String?): String {
    val file = getTargetFilesystem(path) ?: return ""
    val available = getSizeLabel(file.freeSpace)
    return if (SystemInfo.isWindows) {
      val driveName = generateSequence(file, File::getParentFile).last().name
      "$available (drive $driveName)"
    } else {
      available
    }
  }

  @JvmStatic
  fun getTargetFilesystem(path: String?): File? =
    getExistingParentFile(path) ?: File.listRoots().firstOrNull()

  @Contract("null->false")
  @JvmStatic
  fun isExistingSdk(path: String?): Boolean {
    if (path.isNullOrBlank()) {
      return false
    }
    return File(path).run { isDirectory && isValid(this) }
  }

  @Contract("null->false")
  @JvmStatic
  fun isNonEmptyNonSdk(path: String?): Boolean {
    if (path == null) {
      return false
    }
    val file = File(path)
    return file.exists() &&
      !CancellableFileIo.list(file.toPath()).notNullize().isEmpty() &&
      AndroidSdkData.getSdkData(file) == null
  }
}
