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
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidEnvironmentVariableJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedEnvironmentVariableJavaHome
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path

/**
 * A [GradleJdkException] when gradle root [GradleProjectSettings.getGradleJvm] is configured with [USE_JAVA_HOME] macro
 * and the [JAVA_HOME] system environment variable isn't defined, invalid or corrupted.
 */
class InvalidEnvironmentVariableJavaHomeException(
  project: Project,
  gradleRootPath: @SystemIndependent String,
  resolvedGradleJdkPath: Path?
): GradleJdkException(project, gradleRootPath) {

  override val cause =
    if (resolvedGradleJdkPath == null) UndefinedEnvironmentVariableJavaHome else InvalidEnvironmentVariableJavaHome(resolvedGradleJdkPath)
}