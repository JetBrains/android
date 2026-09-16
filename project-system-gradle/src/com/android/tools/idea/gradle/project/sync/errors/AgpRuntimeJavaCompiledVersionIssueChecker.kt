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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleDaemonJvmSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.issue.BuildIssue
import java.nio.file.Path
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper

/**
 * A [RuntimeJavaCompiledVersionIssueChecker] class used as base for related errors regarding runtime Java compiled version, parsing
 * different expected exceptions to add more precise message when AGP requires a newer version of Gradle JVM. The result message:
 *
 * Gradle JVM version incompatible. This project is configured to use an older Gradle JVM that supports up to version X but the current AGP
 * requires a Gradle JVM that supports version Y.
 * - [UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix] apply compatible Gradle JDK configuration and sync
 * - [SelectJdkFromFileSystemQuickFix] that will open the settings tab to configure Gradle JVM
 * - [OpenLinkQuickFix] with message "See AGP Release Notes..."
 */
@Suppress("UnstableApiUsage")
abstract class AgpRuntimeJavaCompiledVersionIssueChecker : RuntimeJavaCompiledVersionIssueChecker() {

  override val failure: AndroidStudioEvent.GradleSyncFailure = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP

  override fun createJdkVersionIncompatibleBuildIssue(
    dependencyMinCompatibleJdkVersion: String,
    gradleJdkVersion: String,
    projectPath: Path,
    gradleVersion: GradleVersion,
    exception: Throwable,
  ): BuildIssue {
    return BuildIssueComposer("Gradle JVM version incompatible.")
      .apply {
        addDescriptionOnNewLine(
          "This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
            "current AGP requires a Gradle JVM that supports version $dependencyMinCompatibleJdkVersion."
        )
        startNewParagraph()

        if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectPath, gradleVersion)) {
          addQuickFix(UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix(gradleVersion, projectPath.toString()))
          addQuickFix(OpenGradleDaemonJvmSettingsQuickFix)
        } else {
          addQuickFix(UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix(gradleVersion, projectPath.toString()))
          addQuickFix(SelectJdkFromFileSystemQuickFix())
        }
        addQuickFix("See AGP Release Notes...", OpenLinkQuickFix("https://developer.android.com/studio/releases/gradle-plugin"))
      }
      .composeBuildIssue()
  }
}
