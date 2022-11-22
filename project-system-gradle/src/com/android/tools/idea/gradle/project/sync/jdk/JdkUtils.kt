/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.android.tools.idea.sdk.extensions.isEqualTo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.jetbrains.rd.util.firstOrNull
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val LOG = Logger.getInstance(JdkUtils::class.java)

object JdkUtils {

  /**
   * Obtain the path with max version JDK from [GradleSettings.getLinkedProjectsSettings] taking in
   * consideration the different gradle project roots and return first sorting by suggested name
   * that combines the provider and version i.e: jbr-17
   * @param project One of the projects currently open in the IDE.
   * @return Jdk path if was possible to obtain
   */
  fun getMaxVersionJdkPathFromAllGradleRoots(project: Project): String? {
    val maxVersionJdkPaths = GradleSettings.getInstance(project).linkedProjectsSettings
      .mapNotNull { AndroidStudioGradleInstallationManager.getInstance().getGradleJvmPath(project, it.externalProjectPath) }
      .groupBy { Jdks.getInstance().findVersion(Path(it)) }
      .mapValues { it.value.toSet() }
      .toSortedMap(compareByDescending { it?.ordinal })
      .firstOrNull()
      ?.value ?: return null

    return maxVersionJdkPaths
      .associateBy { JavaSdk.getInstance().suggestSdkName(null, it) }
      .toSortedMap()
      .values
      .toSet()
      .firstOrNull()
  }

  /**
   * Updates project jdk used to resolve symbols given a jdk path after a valid jdk.table.xml
   * entry has been added or recreated in case was already present but corrupted
   * @param project One of the projects currently open in the IDE.
   * @param jdkPath A jdk absolute path
   */
  fun updateProjectJdkWithPath(project: Project, jdkPath: String) {
    if (!ExternalSystemJdkUtil.isValidJdk(jdkPath)) {
      LOG.info("Unable to update project Jdk given invalid path: $jdkPath")
      return
    }
    val jdkName = addOrRecreateDedicatedJdkTableEntry(jdkPath)
    ProjectJdkTable.getInstance().findJdk(jdkName)?.let { jdk ->
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      if (projectSdk == null || !projectSdk.isEqualTo(jdk)) {
        JavaSdkUtil.applyJdkToProject(project, jdk)
        LOG.info("Updated project Jdk to: ${jdk.name}")
      }
    }
  }

  /**
   * Configure given a gradle root project the [GradleProjectSettings.setGradleJvm] to use the dedicated #JAVA_HOME macro
   * @param project Project to be modified
   * @param gradleRootPath Gradle project root absolute path
   */
  fun setProjectGradleJvmToUseJavaHome(project: Project, gradleRootPath: @SystemIndependent String) {
    updateProjectGradleJvm(project, gradleRootPath, ExternalSystemJdkUtil.USE_JAVA_HOME)
  }

  /**
   * Configure given a gradle root project the [GradleProjectSettings.setGradleJvm] to use the Embedded JDK
   * @param project Project to be modified
   * @param gradleRootPath Gradle project root absolute path
   * @return The jdkTable entry created for the embedded jdk
   */
  fun setProjectGradleJvmToUseEmbeddedJdk(project: Project, gradleRootPath: @SystemIndependent String): String? {
    return IdeSdks.getInstance().embeddedJdkPath?.absolutePathString()?.let {
      val jdkTableEntry = addOrRecreateDedicatedJdkTableEntry(it)
      updateProjectGradleJvm(project, gradleRootPath, jdkTableEntry)
      jdkTableEntry
    }
  }

  private fun updateProjectGradleJvm(
    project: Project,
    gradleRootPath: @SystemIndependent String,
    gradleJvm: String
  ) {
    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleRootPath)
    projectSettings?.gradleJvm = gradleJvm
  }

  /**
   * Create or recreate in case is already present but was corrupted a dedicated jdk.table.xml entry given a valid jdk path.
   * The dedicated name is generated using the suggested name given a Jdk path were combines the provider and version i.e: jbr-17
   * @param jdkPath A valid jdk absolute path
   * @return Sdk name of table entry for the gradle jvm path if was possible to create or update it
   */
  private fun addOrRecreateDedicatedJdkTableEntry(jdkPath: String): String {
    val suggestedJdkName = JavaSdk.getInstance().suggestSdkName(null, jdkPath)
    IdeSdks.getInstance().recreateOrAddJdkInTable(jdkPath, suggestedJdkName)
    return suggestedJdkName
  }
}