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

import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val LOG = Logger.getInstance(MigrateGradleJvmFromMacrosSyncListener::class.java)

/**
 * This [GradleSyncListenerWithRoot] is responsible for migrating Gradle projects away from non-desired supported
 * macros defined on [ExternalSystemJdkUtil] using platform convention: vendor + version i.e. jbr-17.
 */
class MigrateGradleJvmFromMacrosSyncListener : GradleSyncListenerWithRoot {

  override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    when (projectRootSettings?.gradleJvm) {
      ExternalSystemJdkUtil.USE_PROJECT_JDK, null ->
        setProjectGradleJvmWithProjectJdk(project, projectRootSettings) ?: WriteAction.computeAndWait<Unit, Throwable> {
          JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, rootProjectPath)
        }?.let { gradleJvm ->
          LOG.info("Project Gradle root: $rootProjectPath gradleJvm updated from ${ExternalSystemJdkUtil.USE_PROJECT_JDK} to $gradleJvm")
        }
    }
  }

  private fun setProjectGradleJvmWithProjectJdk(
    project: Project,
    projectRootSettings: GradleProjectSettings?
  ) = ProjectRootManager.getInstance(project).projectSdk
    ?.takeIf { ExternalSystemJdkUtil.isValidJdk(it) }
    ?.let { projectJdk ->
      projectRootSettings?.gradleJvm = projectJdk.name
      projectJdk.name
    }
}
