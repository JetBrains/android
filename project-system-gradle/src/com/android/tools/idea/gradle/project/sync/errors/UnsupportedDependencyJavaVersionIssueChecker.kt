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

import com.android.tools.idea.gradle.project.sync.extensions.findWrapperOf
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleDaemonJvmSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.util.lang.JavaVersion
import java.nio.file.Path
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper

private val DEPENDENCY_NAME_REGEX = Regex("Could not resolve\\s+(.*)\\.")

/**
 * A [RuntimeJavaCompiledVersionIssueChecker] for build exception with messages following this format: Dependency requires at least JVM
 * runtime version <Minimum supported JDK>. This build uses a Java <Gradle JDK version> JVM.
 */
@Suppress("UnstableApiUsage")
class UnsupportedDependencyJavaVersionIssueChecker : RuntimeJavaCompiledVersionIssueChecker() {

  override val expectedErrorRegex = Regex("Dependency requires at least JVM runtime version (\\d+). This build uses a Java (\\d+) JVM.")

  override val failure: AndroidStudioEvent.GradleSyncFailure =
    AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_DEPENDENCY

  override fun parseErrorRegexMatch(matchResult: MatchResult): Pair<String, String>? {
    val dependencyMinJdkVersion = matchResult.groups[1]?.value
    val currentGradleJdkVersion = matchResult.groups[2]?.value
    if (dependencyMinJdkVersion == null || currentGradleJdkVersion == null) return null

    return Pair(dependencyMinJdkVersion, currentGradleJdkVersion)
  }

  override fun createJdkVersionIncompatibleBuildIssue(
    dependencyMinCompatibleJdkVersion: String,
    gradleJdkVersion: String,
    projectPath: Path,
    gradleVersion: GradleVersion,
    exception: Throwable,
  ): BuildIssue {
    val dependencyName = getDependencyNameFromException(exception)
    return BuildIssueComposer("Gradle JVM version incompatible.")
      .apply {
        addDescriptionOnNewLine(
          "This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
            "dependency '$dependencyName' requires a Gradle JVM that supports version $dependencyMinCompatibleJdkVersion."
        )
        startNewParagraph()

        if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectPath, gradleVersion)) {
          addQuickFix(
            UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix(projectPath.toString()) {
              JavaVersion.parse(dependencyMinCompatibleJdkVersion)
            }
          )
          addQuickFix(OpenGradleDaemonJvmSettingsQuickFix)
        } else {
          addQuickFix(UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix(gradleVersion, projectPath.toString()))
          addQuickFix(SelectJdkFromFileSystemQuickFix())
        }
      }
      .composeBuildIssue()
  }

  private fun getDependencyNameFromException(exception: Throwable): String {
    val exceptionWrapper = exception.findWrapperOf { message != null && expectedErrorRegex.containsMatchIn(message!!) } ?: return "Unknown"
    val dependencyName = DEPENDENCY_NAME_REGEX.find(exceptionWrapper.toString())?.destructured?.component1() ?: return "Unknown"
    return dependencyName
  }
}
