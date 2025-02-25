/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.flags.StudioFlags.MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME
import com.android.tools.idea.gradle.project.ProjectMigrationsPersistentState
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.io.File

/**
 * This [GradleSyncListenerWithRoot] is responsible given Gradle root project to migrate the current JDK configuration
 * to [USE_GRADLE_LOCAL_JAVA_HOME] macro after a successful Gradle sync, populating [GradleConfigProperties] with the current JDK path
 */
@Suppress("UnstableApiUsage")
class MigrateJdkConfigToGradleJavaHomeListener : GradleSyncListenerWithRoot {

  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    if (!MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME.get()) return

    val projectMigrations = ProjectMigrationsPersistentState.getInstance(project)
    if (projectMigrations.migratedGradleRootsToGradleLocalJavaHome.contains(rootProjectPath)) return

    val gradleSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    when (gradleSettings?.gradleJvm) {
      USE_GRADLE_LOCAL_JAVA_HOME, USE_GRADLE_JAVA_HOME, USE_JAVA_HOME, USE_INTERNAL_JAVA, JDK_LOCATION_ENV_VARIABLE_NAME -> return
      else -> {
        if (IdeSdks.getInstance().isUsingEnvVariableJdk) return

        migrateToGradleLocalJavaHome(project, rootProjectPath, projectMigrations, gradleSettings)
      }
    }
  }

  private fun migrateToGradleLocalJavaHome(
    project: Project,
    rootProjectPath: @SystemIndependent String,
    projectMigrations: ProjectMigrationsPersistentState,
    gradleSettings: GradleProjectSettings?
  ) {
    GradleInstallationManager.getInstance().getGradleJvmPath(project, rootProjectPath)?.let { gradleJdkPath ->
      GradleConfigProperties(File(rootProjectPath)).apply {
        javaHome = File(gradleJdkPath)
        save()
      }
      gradleSettings?.gradleJvm = USE_GRADLE_LOCAL_JAVA_HOME
      projectMigrations.migratedGradleRootsToGradleLocalJavaHome.add(rootProjectPath)

      showMigratedJdkConfigNotification(project, rootProjectPath)
    }
  }

  private fun showMigratedJdkConfigNotification(
    project: Project,
    rootProjectPath: @SystemIndependent String,
  ) {
    val hyperlinks: List<NotificationHyperlink> = listOfNotNull(
      OpenUrlHyperlink(
        AndroidBundle.message("project.migrated.to.gradle.local.java.home.info.url"),
        AndroidBundle.message("project.migrated.to.gradle.local.java.home.info")
      ),
      SelectJdkFromFileSystemHyperlink.create(
        project,
        rootProjectPath,
        text = AndroidBundle.message("project.migrated.to.gradle.local.java.home.button")
      )
    )

    AndroidNotification.getInstance(project).showBalloon(
      AndroidBundle.message("project.migrated.to.gradle.local.java.home.title"),
      AndroidBundle.message("project.migrated.to.gradle.local.java.home.message", File(rootProjectPath).name),
      NotificationType.INFORMATION,
      *hyperlinks.toTypedArray()
    )
  }
}