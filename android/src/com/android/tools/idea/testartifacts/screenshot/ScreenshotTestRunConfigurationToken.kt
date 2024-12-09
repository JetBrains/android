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
package com.android.tools.idea.testartifacts.screenshot

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface ScreenshotTestRunConfigurationToken<P: AndroidProjectSystem> : Token {
  /**
   * @return the module containing screenshotTest code related to [module].
   */
  fun getModuleForScreenshotTestRunConfiguration(projectSystem: P, module: Module): Module?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<ScreenshotTestRunConfigurationToken<AndroidProjectSystem>>(
      "com.android.tools.idea.testartifacts.screenshot.screenshotTestRunConfigurationToken")

    @JvmStatic
    fun getModuleForScreenshotTestRunConfiguration(module: Module) =
      module.project.getProjectSystem().let {
        it.getTokenOrNull(EP_NAME)?.getModuleForScreenshotTestRunConfiguration(it, module)
      }
  }
}