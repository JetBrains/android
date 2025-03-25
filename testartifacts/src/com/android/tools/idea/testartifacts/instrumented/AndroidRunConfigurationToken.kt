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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface AndroidRunConfigurationToken<P: AndroidProjectSystem> : Token {
  /**
   * @return the module containing production code related to [module].
   */
  fun getModuleForAndroidRunConfiguration(projectSystem: P, module: Module): Module
  /**
   * @return the module containing androidTest code related to [module].
   */
  fun getModuleForAndroidTestRunConfiguration(projectSystem: P, module: Module): Module?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<AndroidRunConfigurationToken<AndroidProjectSystem>>(
      "com.android.tools.idea.testartifacts.instrumented.androidRunConfigurationToken")

    // TODO(xof): should this be nullable?  At the moment we assume that all (android) modules have a main module that is runnable, but
    //  that is in not in fact the case in the presence of (Gradle) projects with the test plugin.
    @JvmStatic
    fun getModuleForAndroidRunConfiguration(module: Module) =
      module.project.getProjectSystem().let {
        it.getTokenOrNull(EP_NAME)?.getModuleForAndroidRunConfiguration(it, module)
      }
      ?: module

    @JvmStatic
    fun getModuleForAndroidTestRunConfiguration(module: Module) =
      module.project.getProjectSystem().let {
        it.getTokenOrNull(EP_NAME)?.getModuleForAndroidTestRunConfiguration(it, module)
      }
  }
}