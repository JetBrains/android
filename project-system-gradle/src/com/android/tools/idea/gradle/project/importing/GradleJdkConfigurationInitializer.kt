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
package com.android.tools.idea.gradle.project.importing

import com.android.tools.idea.flags.StudioFlags.GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS
import com.android.tools.idea.gradle.config.GradleConfigManager
import com.android.tools.idea.gradle.extensions.isDaemonJvmCriteriaRequiredForNewProjects
import com.android.tools.idea.gradle.extensions.isProjectUsingDaemonJvmCriteria
import com.android.tools.idea.gradle.project.ProjectMigrationsPersistentState
import com.android.tools.idea.gradle.project.sync.jdk.ProjectJdkUtils
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaInitializer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.util.concurrent.CompletableFuture

@Service
class GradleJdkConfigurationInitializer private constructor() {

  companion object {
    @JvmStatic
    fun getInstance(): GradleJdkConfigurationInitializer {
      return ApplicationManager.getApplication().getService(GradleJdkConfigurationInitializer::class.java)
    }
  }

  private val logger = Logger.getInstance(GradleJdkConfigurationInitializer::class.java)

  @VisibleForTesting
  var canInitializeDaemonJvmCriteria = !ApplicationManager.getApplication().isUnitTestMode

  fun initialize(
    project: Project,
    externalProjectPath: @SystemIndependent String,
    projectSettings: GradleProjectSettings,
    newProjectConfiguration: GradleNewProjectConfiguration
  ) {
    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, projectSettings.externalProjectPath)) {
      // Skip initialization and reuse the already defined daemon JVM criteria
      return
    }

    setUpDaemonJvmCriteria(project, externalProjectPath, projectSettings, newProjectConfiguration).handle { result, exception ->
      if (result == false || exception != null) {
        if (GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS.get() || ApplicationManager.getApplication().isUnitTestMode) {
          setUpLocalJavaHomeAsGradleJvm(project, externalProjectPath, projectSettings)
        } else {
          setUpProjectJdkAsGradleJvm(project, projectSettings)
        }
      }
    }.get()
  }

  private fun setUpDaemonJvmCriteria(
    project: Project,
    externalProjectPath: @SystemIndependent String,
    projectSettings: GradleProjectSettings,
    newProjectConfiguration: GradleNewProjectConfiguration
  ): CompletableFuture<Boolean> {
    GradleInstallationManager.guessGradleVersion(projectSettings)?.let { gradleVersion ->
      // Test projects will continue using the old behaviour until b/392565928 is addressed separately
      if (GradleDaemonJvmHelper.isDaemonJvmCriteriaRequiredForNewProjects(gradleVersion) && canInitializeDaemonJvmCriteria) {
        return GradleDaemonJvmCriteriaInitializer(project, externalProjectPath, gradleVersion).initialize(
          newProjectConfiguration.useDefaultDaemonJvmCriteria
        ).whenComplete { _, error ->
          if (error != null) {
            logger.warn("Unable to initialize project Daemon JVM criteria", error)
          }
        }
      }
    }
    return CompletableFuture.completedFuture(false)
  }

  private fun setUpLocalJavaHomeAsGradleJvm(
    project: Project,
    externalProjectPath: @SystemIndependent String,
    projectSettings: GradleProjectSettings,
  ) {
    projectSettings.gradleJvm = USE_GRADLE_LOCAL_JAVA_HOME
    GradleConfigManager.initializeJavaHome(project, externalProjectPath)
    val projectMigration = ProjectMigrationsPersistentState.getInstance(project)
    projectMigration.migratedGradleRootsToGradleLocalJavaHome.add(externalProjectPath)
  }

  private fun setUpProjectJdkAsGradleJvm(project: Project, projectSettings: GradleProjectSettings) {
    projectSettings.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
    ProjectJdkUtils.setUpEmbeddedJdkAsProjectJdk(project)
  }
}