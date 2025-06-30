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

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.extensions.isEqualTo
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val LOG = Logger.getInstance(ProjectJdkUtils::class.java)

/**
 * Collection of utils for the Project JDK stored under 'project-jdk-name' attribute
 * on '.idea/misc.xml' file located on project root. Its value represented usually as
 * vendor-version i.e. jbr-17 points to a JDK table.
 *
 * The Project JDK is used for those modules that doesn't define their own JDK to inherit it
 * allowing to resolve project symbols.
 */
object ProjectJdkUtils {

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
    val jdkName = ProjectJdkTableUtils.addOrRecreateDedicatedJdkTableEntry(jdkPath)
    ProjectJdkTable.getInstance().findJdk(jdkName)?.let { jdk ->
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      if (projectSdk == null || !projectSdk.isEqualTo(jdk)) {
        JavaSdkUtil.applyJdkToProject(project, jdk)
        LOG.info("Updated project Jdk to: ${jdk.name}")
      }
    }
  }

  /**
   * Update gradleJvm of project linked settings stored under .idea/gradle.xml which indicates where JDK is located when current
   * Gradle JDK configuration is resolved to execute build
   * @param project Project to be modified
   * @param gradleRootPath Gradle project root absolute path
   * @param gradleJvm This can be supported macros like #JAVA_HOME, #GRADLE_LOCAL_JAVA_HOME but also jdk.table.xml entries
   */
  fun updateProjectGradleJvm(
    project: Project,
    gradleRootPath: @SystemIndependent String,
    gradleJvm: String
  ) {
    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleRootPath)
    projectSettings?.gradleJvm = gradleJvm
  }

  fun setUpEmbeddedJdkAsProjectJdk(project: Project) {
    WriteAction.runAndWait<RuntimeException> {
      val embeddedJdkPath = IdeSdks.getInstance().embeddedJdkPath
      val jdkTableEntry = ProjectJdkTableUtils.addOrRecreateDedicatedJdkTableEntry(embeddedJdkPath.toString())
      ProjectJdkTable.getInstance().findJdk(jdkTableEntry)?.let {
        ProjectRootManager.getInstance(project).projectSdk = it
      }
    }
  }
}