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

import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings

class AndroidStudioGradleInstallationManager : GradleInstallationManager() {

  companion object {
    @JvmStatic
    val instance: AndroidStudioGradleInstallationManager
      get() = application.service<GradleInstallationManager>() as AndroidStudioGradleInstallationManager
  }

  @Deprecated(
    "use resolveGradleJvmPath, since getGradleJvmPath reports an error when current thread doesn't have a ProgressIndicator or Job",
    ReplaceWith("resolveGradleJvmPath"),
  )
  override fun getGradleJvmPath(project: Project, linkedProjectPath: String): String? {
    return super.getGradleJvmPath(project, linkedProjectPath)
  }

  @Suppress("DEPRECATION")
  suspend fun resolveGradleJvmPath(project: Project, linkedProjectPath: String): String? {
    return getGradleJvmPath(project, linkedProjectPath)
  }

  suspend fun resolveGradleJvmVersion(project: Project, projectSettings: GradleProjectSettings): JavaVersion? {
    return if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectSettings)) {
      val gradleJvmCriteria = GradleDaemonJvmPropertiesFile.getProperties(projectSettings.externalProjectPath.asPath())
      gradleJvmCriteria.version?.value?.toIntOrNull()?.let { feature -> JavaVersion.compose(feature = feature) }
    } else {
      val jdkPath = resolveGradleJvmPath(project, projectSettings.externalProjectPath) ?: return null
      Jdks.getInstance().getVersion(jdkPath)?.maxLanguageLevel?.toJavaVersion()
    }
  }
}
