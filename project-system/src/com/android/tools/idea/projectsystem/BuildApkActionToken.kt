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

/**
 * Project system extension point for triggering APK build operations from top-level IDE actions (e.g. `GenerateApkAction` under **Build >
 * Build Bundle(s) / APK(s) > Build APK(s)**).
 *
 * Because top-level menu actions operate at the [Project] level, each project system implementation (`BuildApkActionToken`) is responsible
 * for:
 * 1. Determining whether the project contains any app modules/targets suitable for APK generation ([isAppModulePresent]).
 * 2. Resolving the appropriate build targets or displaying target-selection UI/notifications as needed ([buildApk]).
 * 3. Triggering the underlying build invoker for those targets.
 */
interface BuildApkActionToken<P : AndroidProjectSystem> : Token {
  /** Returns true if the project contains at least one app module that supports generating an APK. */
  fun isAppModulePresent(project: Project): Boolean

  /** Triggers building the APK(s) for app modules in the project. */
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
