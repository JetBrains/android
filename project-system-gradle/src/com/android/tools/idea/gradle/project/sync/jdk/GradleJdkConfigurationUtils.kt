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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.io.File
import kotlin.io.path.Path

/**
 * Collection of utils for Gradle JDK configuration stored under 'gradleJvm' option
 * on '.idea/gradle.xml' file located on project root. The defined configuration will be
 * used to create Daemon JVM process for the Gradle build/sync execution.
 *
 * NOTE: In presence of Daemon JVM criteria and compatible Gradle version for it, the specified
 * Gradle JDK configuration will be ignored. Check [GradleDaemonJvmHelper] on how to modify
 * the criteria for those projects.
 */
object GradleJdkConfigurationUtils {

  /**
   * Obtain the path with max version JDK from [GradleSettings.getLinkedProjectsSettings] taking in
   * consideration the different gradle project roots and return first sorting by suggested name
   * that combines the provider and version i.e: jbr-17
   * @param project One of the projects currently open in the IDE.
   * @return Jdk path if was possible to obtain
   */
  fun getMaxVersionJdkPathFromAllGradleRoots(project: Project): String? {
    val maxVersionJdkPaths = GradleSettings.getInstance(project).linkedProjectsSettings
                               .mapNotNull { GradleInstallationManager.getInstance().getGradleJvmPath(project, it.externalProjectPath) }
                               .groupBy { Jdks.getInstance().findVersion(Path(it)) }
                               .mapValues { it.value.toSet() }
                               .toSortedMap(compareByDescending { it?.ordinal })
                               .firstNotNullOfOrNull { it.value }
                               ?: return null

    return maxVersionJdkPaths
      .associateBy { JavaSdk.getInstance().suggestSdkName(null, it) }
      .toSortedMap()
      .values
      .toSet()
      .firstOrNull()
  }

  /**
   * Configure given a project with single gradle root the [GradleProjectSettings.setGradleJvm] to use the Gradle JDK.
   * @param project Project to be modified
   * @param jdkPath Jdk absolute path
   */
  fun setProjectGradleJdkWithSingleGradleRoot(project: Project, jdkPath: @SystemIndependent String) {
    project.basePath?.let { gradleRootPath ->
      setProjectGradleJdk(project, gradleRootPath, jdkPath)
    }
  }

  /**
   * Configure given a gradle root project the [GradleProjectSettings.setGradleJvm] to use the Gradle JDK
   * @param project Project to be modified
   * @param gradleRootPath Gradle project root absolute path
   * @param jdkPath Jdk absolute path
   * @return The jdkTable entry created only in case the project isn't using the [USE_GRADLE_LOCAL_JAVA_HOME] that doesn't require
   * a valid entry in order to trigger gradle sync
   */
  @Suppress("UnstableApiUsage")
  fun setProjectGradleJdk(project: Project, gradleRootPath: @SystemIndependent String, jdkPath: @SystemIndependent String): String? {
    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleRootPath)
    return when (projectSettings?.gradleJvm) {
      USE_GRADLE_LOCAL_JAVA_HOME -> {
        GradleConfigProperties(File(gradleRootPath)).apply {
          javaHome = File(jdkPath)
          save()
        }
        null
      }
      else -> {
        val jdkTableEntry = ProjectJdkTableUtils.addOrRecreateDedicatedJdkTableEntry (jdkPath)
        ProjectJdkUtils.updateProjectGradleJvm (project, gradleRootPath, jdkTableEntry)
        jdkTableEntry
      }
    }
  }
}