/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface BuildApkActionToken<P : AndroidProjectSystem> : Token {
  fun isAppModulePresent(project: Project): Boolean

  fun buildApk(project: Project)

  companion object {
    val EP_NAME = ExtensionPointName<BuildApkActionToken<AndroidProjectSystem>>("com.android.tools.idea.projectsystem.buildApkActionToken")

    @JvmStatic
    fun isSupported(project: Project): Boolean {
      return project.getProjectSystem().getTokenOrNull(EP_NAME)?.isAppModulePresent(project) ?: false
    }

    @JvmStatic
    fun execute(project: Project) {
      project.getProjectSystem().getTokenOrNull(EP_NAME)?.buildApk(project)
    }
  }
}
