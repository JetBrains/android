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
package com.android.tools.idea.gradle.project.sync.jdk.exceptions

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.base.GradleJdkException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradleLocalJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradleLocalJavaHome
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.nio.file.Path

/**
 * A [GradleJdkException] when gradle root [GradleProjectSettings.getGradleJvm] is configured with [USE_GRADLE_LOCAL_JAVA_HOME] macro
 * and the "java.home" field under ".gradle/config.properties" file isn't present, invalid or corrupted.
 */
class InvalidGradleLocalJavaHomeException(
  project: Project,
  gradleRootPath: @SystemIndependent String,
  resolvedGradleJdkPath: Path?
) : GradleJdkException(project, gradleRootPath) {

  override val cause =
    if (resolvedGradleJdkPath == null) UndefinedGradleLocalJavaHome else InvalidGradleLocalJavaHome(resolvedGradleJdkPath)

  override val recoveryJdkCandidates = listOf(
    RecoveryCandidate(
      jdkName = AndroidBundle.message("gradle.project.jdk.name"),
      jdkPath = ProjectRootManager.getInstance(project).projectSdk?.homePath.orEmpty(),
      reason = "")
  ).plus(super.recoveryJdkCandidates)
}