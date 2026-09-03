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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleDaemonJvmSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * Checks if the Gradle JVM used by the project is compatible with the current Gradle version, and displays a warning dialog if an
 * incompatibility is detected.
 */
class GradleJvmCompatibilityChecker(private val scope: CoroutineScope) {

  companion object {
    suspend fun Project.isProjectUsingIncompatibleGradleJvm() =
      getService(GradleJvmCompatibilityChecker::class.java).checkIncompatibleGradleJvmForProject(this)
  }

  suspend fun checkIncompatibleGradleJvmForProject(project: Project): Boolean {
    if (!StudioFlags.EXECUTE_GRADLE_JVM_COMPATIBILITY_CHECK.get()) return false
    if (!IdeInfo.getInstance().isAndroidStudio) return false
    val gradleProjectSettings = GradleSettings.getInstance(project).linkedProjectsSettings.firstOrNull() ?: return false
    val currentJavaVersion =
      AndroidStudioGradleInstallationManager.instance.resolveGradleJvmVersion(project, gradleProjectSettings) ?: return false

    val gradleJvmCompatibility = GradleJvmCompatibilityResolver.resolve(project, gradleProjectSettings.resolveGradleVersion())
    if (gradleJvmCompatibility.isCompatible(currentJavaVersion)) return false

    scope.launch(Dispatchers.EDT) {
      displayIncompatibleGradleJvmDialog(project, currentJavaVersion, gradleJvmCompatibility, gradleProjectSettings)
    }
    return true
  }

  private fun displayIncompatibleGradleJvmDialog(
    project: Project,
    currentJavaVersion: JavaVersion,
    gradleJvmCompatibility: GradleJvmCompatibilityInfo,
    gradleProjectSettings: GradleProjectSettings,
  ) {
    var applyCompatibleJvmAction: DescribedBuildIssueQuickFix =
      UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix(gradleProjectSettings.externalProjectPath) {
        gradleJvmCompatibility.recommendedJavaVersion
      }
    var openSettingsAction: DescribedBuildIssueQuickFix = SelectJdkFromFileSystemQuickFix()

    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(gradleProjectSettings)) {
      applyCompatibleJvmAction =
        UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix(gradleProjectSettings.externalProjectPath) {
          gradleJvmCompatibility.recommendedJavaVersion
        }
      openSettingsAction = OpenGradleDaemonJvmSettingsQuickFix
    }

    GradleJvmIncompatibleDialog.showDialog(
      project,
      currentJavaVersion,
      gradleJvmCompatibility,
      applyCompatibleJvmAction,
      openSettingsAction,
    )
  }
}
