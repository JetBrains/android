/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.util.lang.JavaVersion
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.Path
import org.gradle.util.GradleVersion
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper

private const val UNSUPPORTED_JDK_VERSION_EXCEPTION = "Unsupported class file major version"

/**
 * A [GradleIssueChecker] that provides quick-fixes for compatibility issues between project's Gradle version and
 * selected Gradle JVM version following [Gradle compatibility table](https://docs.gradle.org/current/userguide/compatibility.html).
 * In addition, overrides the platform [org.jetbrains.plugins.gradle.issue.IncompatibleGradleJvmAndGradleIssueChecker] to provide
 * better error description but also custom quick-fixes.
 */
class IncompatibleGradleJvmAndGradleIssueChecker: GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val gradleVersion = getGradleVersion(issueData) ?: return null
    val projectPath = Path(issueData.projectPath)
    val javaVersion = getJavaVersion(issueData, projectPath, gradleVersion)

    if (javaVersion != null) {
      if (!GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)) {
        // Log metrics
        SyncFailureUsageReporter.getInstance().collectFailure(
          issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP
        )

        return createBuildIssue(javaVersion, gradleVersion)
      }
    }
    return null
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ) = failureCause.contains(UNSUPPORTED_JDK_VERSION_EXCEPTION)

  private fun getGradleVersion(issueData: GradleIssueData): GradleVersion? {
    return issueData.buildEnvironment?.let {
      GradleVersion.version(it.gradle.gradleVersion)
    }
  }

  private fun getJavaVersion(issueData: GradleIssueData, projectPath: Path, gradleVersion: GradleVersion): JavaVersion? {
    // Projects using Daemon JVM criteria with a compatible Gradle version will ignore javaHome defined on BuildEnvironment
    // for this reason defined version in gradle/gradle-daemon-jvm.properties is used to run build instead
    if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectPath, gradleVersion)) {
      GradleDaemonJvmPropertiesFile.getProperties(projectPath).version?.value?.let {
        return JavaVersion.parse(it)
      }
    }

    return issueData.buildEnvironment?.let {
      ExternalSystemJdkUtil.getJavaVersion(it.java.javaHome.path)
    }
  }

  private fun createBuildIssue(javaVersion: JavaVersion, gradleVersion: GradleVersion): BuildIssue {
    return BuildIssueComposer(
      baseMessage = AndroidBundle.message("android.build.issue.incompatible.gradle.jvm.title"),
      issueTitle = AndroidBundle.message("android.build.issue.incompatible.gradle.jvm.title")
    ).apply {
      val minimumSupportedJavaVersion = GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion)
      val maximumSupportedJavaVersion = GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(gradleVersion)
      addDescriptionOnNewLine(AndroidBundle.message(
        "android.build.issue.incompatible.gradle.jvm.description",
        gradleVersion.version, javaVersion.feature, minimumSupportedJavaVersion, maximumSupportedJavaVersion)
      )
    }.composeBuildIssue()
  }
}