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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.InvalidEnvironmentVariableJavaHomeException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.InvalidEnvironmentVariableStudioGradleJdkException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.InvalidGradleLocalJavaHomeException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.InvalidGradlePropertiesJavaHomeException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.InvalidTableEntryJdkException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.base.GradleJdkException
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.nio.file.Path

/**
 * Manager that validates the project JDK configuration adding more granularity on the IntelliJ exception [ExternalSystemJdkException]
 * allowing us to notify properly the user, improving recovery suggestions and error tracking
 */
class GradleJdkValidationManager private constructor() {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleJdkValidationManager = project.getService(GradleJdkValidationManager::class.java)
  }

  fun validateProjectGradleJvmPath(
    project: Project,
    gradleRootPath: @SystemIndependent String
  ): GradleJdkException? {
    val resolvedGradleJdkPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, gradleRootPath)?.asPath()?.let { gradleJdkPath ->
      val validJdkPath = IdeSdks.getInstance().validateJdkPath(gradleJdkPath)
      if (validJdkPath != null) return null
      gradleJdkPath
    }

    val gradleJdkException = if (IdeSdks.getInstance().isUsingEnvVariableJdk) {
      InvalidEnvironmentVariableStudioGradleJdkException(project, gradleRootPath, resolvedGradleJdkPath)
    } else {
      getInvalidJdkExceptionBasedOnGradleJvm(project, gradleRootPath, resolvedGradleJdkPath)
    }
    gradleJdkException?.let {
      JdkAnalyticsTracker.reportInvalidJdkException(project, it.reason)
    }
    return gradleJdkException
  }

  @Suppress("UnstableApiUsage")
  private fun getInvalidJdkExceptionBasedOnGradleJvm(
    project: Project,
    gradleRootPath: @SystemIndependent String,
    resolvedGradleJdkPath: Path?
  ): GradleJdkException? {
    val gradleSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(gradleRootPath)
    return when (val gradleJvm = gradleSettings?.gradleJvm) {
      null, ExternalSystemJdkUtil.USE_PROJECT_JDK -> null // We migrate away from those on MigrateGradleJvmSyncListener
      ExternalSystemJdkUtil.USE_INTERNAL_JAVA -> null // Internal Jdk is expected to be always valid
      ExternalSystemJdkUtil.USE_JAVA_HOME -> InvalidEnvironmentVariableJavaHomeException(project, gradleRootPath, resolvedGradleJdkPath)
      USE_GRADLE_JAVA_HOME -> InvalidGradlePropertiesJavaHomeException(project, gradleRootPath, resolvedGradleJdkPath)
      USE_GRADLE_LOCAL_JAVA_HOME -> InvalidGradleLocalJavaHomeException(project, gradleRootPath, resolvedGradleJdkPath)
      JDK_LOCATION_ENV_VARIABLE_NAME -> InvalidEnvironmentVariableStudioGradleJdkException(project, gradleRootPath, resolvedGradleJdkPath)
      else -> InvalidTableEntryJdkException(project, gradleRootPath, gradleJvm)
    }
  }
}