/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.android.tools.idea.adb.AdbFileProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.File
import java.util.function.Supplier

/**
 * Ensures [AdbFileProvider] is available for each new project
 *
 * Note: The reason this code need to live in the "android-core" module
 * is that it depends on [AndroidSdkUtils] which has many dependencies
 * that have not been factored out. Ideally, this class should be part
 * of the "android-adb" module.
 */
class AdbFileProviderInitializer : ProjectManagerListener {
  /**
   * Sets up the [AdbFileProvider] for each [Project]
   *
   * Note: this code runs on the EDT thread, so we need to avoid slow operations.
   */
  override fun projectOpened(project: Project) {
    val supplier = Supplier<File?> { AndroidSdkUtils.getAdb(project) }
    AdbFileProvider(supplier).storeInProject(project)
  }
}
