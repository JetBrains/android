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

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.extensions.isProjectUsingDaemonJvmCriteria
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_DAEMON_JVM_CRITERIA_ERROR_EVENT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_JDK_INVALID
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_JDK_CONFIGURATION
import com.google.wireless.android.sdk.stats.GradleDaemonJvmCriteriaErrorEvent
import com.google.wireless.android.sdk.stats.GradleJdkConfigurationEvent
import com.google.wireless.android.sdk.stats.GradleJdkConfigurationEvent.GradleJdkConfiguration
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME

/**
 * Analytic tracker util that reports any JDK related events using [UsageTracker]
 */
object JdkAnalyticsTracker {

  fun reportInvalidJdkException(project: Project, reason: InvalidJdkReason) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(PROJECT_SYSTEM)
        .setKind(GRADLE_JDK_INVALID)
        .setGradleJdkInvalidEvent(GradleJdkInvalidEvent.newBuilder().setReason(reason))
        .withProjectId(project)
    )
  }

  fun reportDaemonJvmCriteriaException(projectPath: @SystemIndependent String, errorEvent: GradleDaemonJvmCriteriaErrorEvent.Error) {
    val project = ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == projectPath }
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(PROJECT_SYSTEM)
        .setKind(GRADLE_DAEMON_JVM_CRITERIA_ERROR_EVENT)
        .setGradleDaemonJvmCriteriaErrorEvent(GradleDaemonJvmCriteriaErrorEvent.newBuilder().setError(errorEvent))
        .withProjectId(project)
    )
  }

  fun reportGradleJdkConfiguration(project: Project, rootProjectPath: @SystemIndependent String) {
    val currentGradleJdkConfiguration = when {
      GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, rootProjectPath) -> GradleJdkConfiguration.GRADLE_DAEMON_JVM_CRITERIA
      IdeSdks.getInstance().isUsingEnvVariableJdk ->  GradleJdkConfiguration.STUDIO_GRADLE_JDK
      else -> resolveConfigurationFromGradleJvm(project, rootProjectPath)
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(PROJECT_SYSTEM)
        .setKind(GRADLE_JDK_CONFIGURATION)
        .setGradleJdkConfigurationEvent(GradleJdkConfigurationEvent.newBuilder().setConfiguration(currentGradleJdkConfiguration))
        .withProjectId(project)
    )
  }

  private fun resolveConfigurationFromGradleJvm(
    project: Project,
    rootProjectPath: @SystemIndependent String
  ): GradleJdkConfiguration {
    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    return projectRootSettings?.let {
      when (it.gradleJvm) {
        ExternalSystemJdkUtil.USE_INTERNAL_JAVA -> GradleJdkConfiguration.JAVA_INTERNAL
        ExternalSystemJdkUtil.USE_JAVA_HOME -> GradleJdkConfiguration.JAVA_HOME
        USE_GRADLE_JAVA_HOME -> GradleJdkConfiguration.GRADLE_JAVA_HOME
        USE_GRADLE_LOCAL_JAVA_HOME -> GradleJdkConfiguration.GRADLE_LOCAL_JAVA_HOME
        IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME -> GradleJdkConfiguration.STUDIO_GRADLE_JDK
        else -> GradleJdkConfiguration.JDK_TABLE_ENTRY
      }
    } ?: GradleJdkConfiguration.UNDEFINED_JDK_CONFIGURATION
  }
}