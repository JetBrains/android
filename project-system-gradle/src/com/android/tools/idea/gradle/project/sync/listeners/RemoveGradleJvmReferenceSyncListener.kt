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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.gradle.extensions.isProjectUsingDaemonJvmCriteria
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * This [GradleSyncListenerWithRoot] is responsible given Gradle root project to remove the Gradle JVM config stored on .idea/gradle.xml
 * if projects are using [Gradle Daemon JVM criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria).
 * Those projects will ignore any Gradle JDK configuration prioritizing instead the defined criteria.
 *
 * The Gradle JDK configuration will be discarded during auto-migration process to Daemon JVM criteria via IDE, however, for those
 * projects manually migrated the old configuration might still be present, causing confusion about source of truth.
 */
@Suppress("UnstableApiUsage")
class RemoveGradleJvmReferenceSyncListener: GradleSyncListenerWithRoot {

  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    if (!GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, rootProjectPath)) return

    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    if (projectRootSettings?.gradleJvm != null && projectRootSettings.gradleJvm != ExternalSystemJdkUtil.USE_PROJECT_JDK) {
      // Replace with #USE_PROJECT_JDK being this default
      projectRootSettings.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
    }
  }
}