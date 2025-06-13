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
package com.android.tools.idea.gradle.extensions

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import kotlin.io.path.Path

fun GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(
  rootProjectPath: @SystemIndependent String?,
  gradleVersion: GradleVersion?
): Boolean {
  if (rootProjectPath == null) return false
  if (gradleVersion == null) return false
  return isProjectUsingDaemonJvmCriteria(Path(rootProjectPath), gradleVersion)
}

fun GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(
  project: Project,
  rootProjectPath: @SystemIndependent String?
): Boolean {
  if (rootProjectPath == null) return false

  GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)?.let {
   return isProjectUsingDaemonJvmCriteria(it)
  }

  GradleInstallationManager.getInstance().guessBuildLayoutParameters(project, rootProjectPath).gradleVersion?.let {
    return isProjectUsingDaemonJvmCriteria(rootProjectPath, it)
  }

  return GradleSyncStateHolder.getInstance(project).lastSyncedGradleVersion?.let {
    isProjectUsingDaemonJvmCriteria(rootProjectPath, it)
  } ?: false
}

fun GradleDaemonJvmHelper.isDaemonJvmCriteriaRequiredForNewProjects(gradleVersion: GradleVersion) =
  Registry.`is`("gradle.daemon.jvm.criteria.new.project") &&
  gradleVersion >= GradleVersion.version(StudioFlags.NPW_DAEMON_JVM_CRITERIA_REQUIRED_GRADLE_VERSION.get())
