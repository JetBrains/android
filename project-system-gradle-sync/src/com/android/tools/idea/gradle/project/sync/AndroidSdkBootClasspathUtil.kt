/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.utils.FileUtils
import java.io.File

/**
 * This method finds entries in the bootclasspath that should be available in the IDE. These include:
 * - optional libraries
 * - SDK add-ons
 *
 * Note that bootclasspath may contain more entries, such as core-lambda-stubs from the build tools,
 * but that jar should not be available in the IDE.
 */
fun getUsefulBootClasspathLibraries(bootClasspath: Collection<String>): Collection<File> {
  val androidJar = bootClasspath.asSequence().map { File(it) }.firstOrNull { it.name == "android.jar" } ?: return emptyList()
  val optionalDir = androidJar.parentFile.resolve("optional")
  val sdkAddOnDir: File? = androidJar.parentFile?.parentFile?.parentFile?.resolve(SdkConstants.FD_ADDONS)

  fun isOptionalLibrary(file: File): Boolean = file.parentFile.path == optionalDir.path // Assumes 'optional` won't be created as `Optional` etc.
  fun isSdkAddOn(file: File): Boolean = sdkAddOnDir?.let {
    FileUtils.isFileInDirectory(file, sdkAddOnDir)
  } ?: false

  return bootClasspath.asSequence()
    .map { File(it) }
    .filter { isOptionalLibrary(it) || isSdkAddOn(it) }
    .toList()
}