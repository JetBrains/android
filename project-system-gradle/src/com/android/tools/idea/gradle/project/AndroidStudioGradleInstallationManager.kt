/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings

class AndroidStudioGradleInstallationManager : GradleInstallationManager() {
  override fun getGradleJvmPath(project: Project, linkedProjectPath: String): String? {
    val ideSdks = IdeSdks.getInstance()
    // Using environment variable
    if (ideSdks.isUsingEnvVariableJdk()) {
      return ideSdks.getEnvVariableJdkValue()
    }

    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath)
    if (settings != null) {
      val settingsJvm = settings.getGradleJvm()
      if (settingsJvm != null) {
        // Try to resolve from variables before looking in GradleInstallationManager
        when (settingsJvm) {
          IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME -> return ideSdks.getEnvVariableJdkValue()
        }
      }
    }
    // None of the environment variables is used (or is used but invalid), handle it in the same way IDEA does.
    return super.getGradleJvmPath(project, linkedProjectPath)
  }
}