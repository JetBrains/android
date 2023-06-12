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
package com.android.tools.idea.adb

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Supplier of ADB executable path. It can be obtained for the application or a project, although the latter should be preferred.
 *
 * Usage:
 *  `val adbFile: File? = AdbFileProvider.fromProject(project).get()`
 */
fun interface AdbFileProvider {
  fun get(): File?

  companion object {
    @JvmStatic fun fromProject(project: Project) : AdbFileProvider = project.getService(AdbFileProvider::class.java)

    /**
     * It's preferred to use [AdbFileProvider.fromProject] because Application and Project could be using different SDKs.
     * A Project should only use the ADB provided by the SDK used in the Project.
     */
    @JvmStatic fun fromApplication() : AdbFileProvider = ApplicationManager.getApplication().getService(AdbFileProvider::class.java)
  }
}