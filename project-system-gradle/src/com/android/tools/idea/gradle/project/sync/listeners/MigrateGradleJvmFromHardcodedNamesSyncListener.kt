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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.gradle.extensions.isProjectUsingDaemonJvmCriteria
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.ANDROID_STUDIO_DEFAULT_JDK_NAME
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.ANDROID_STUDIO_JAVA_HOME_NAME
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.EMBEDDED_JDK_NAME
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val LOG = Logger.getInstance(MigrateGradleJvmFromHardcodedNamesSyncListener::class.java)

/**
 * This [GradleSyncListenerWithRoot] is responsible for migrating Gradle projects away from the hardcoded JDK naming
 * using platform convention: vendor + version i.e. jbr-17.
 *
 * NOTE: Projects using [Gradle Daemon JVM criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria)
 * will skip this given that defined criteria will take precedence over the Gradle JDK configuration.
 */
open class MigrateGradleJvmFromHardcodedNamesSyncListener : GradleSyncListenerWithRoot {

  override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(project, rootProjectPath)) return

    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    when (projectRootSettings?.gradleJvm) {
      EMBEDDED_JDK_NAME, ANDROID_STUDIO_DEFAULT_JDK_NAME -> WriteAction.computeAndWait<Unit, Throwable> {
        JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, rootProjectPath).let { gradleJvm ->
          LOG.info("Project Gradle root: $rootProjectPath gradleJvm updated from ${projectRootSettings.gradleJvm} to $gradleJvm")
        }
      }
      ANDROID_STUDIO_JAVA_HOME_NAME -> {
        projectRootSettings.gradleJvm = USE_JAVA_HOME
        LOG.info("Project Gradle root: $rootProjectPath gradleJvm updated from $ANDROID_STUDIO_JAVA_HOME_NAME to $USE_JAVA_HOME")
      }
    }
  }
}
