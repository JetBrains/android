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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.tools.idea.gradle.project.sync.model.GradleDaemonToolchain
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.JdkConstants
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import java.io.File
import org.jetbrains.plugins.gradle.issue.quickfix.GradleOpenDaemonJvmSettingsQuickFix
import org.junit.Test

class IncompatibleGradleJvmAndGradleIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun `test Given old project using an incompatible newer JDK When sync Then expected build output exception is thrown`() {
    val preparedProject = projectRule.prepareTestProject(
      testProject = AndroidCoreTestProject.SIMPLE_APPLICATION,
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_80
    )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = File(JdkConstants.JDK_21_PATH),
      verifyBuildIssue = { _, buildIssue ->
        expect.that(buildIssue.title).contains("Incompatible Gradle JVM version")
        expect.that(buildIssue.description).contains(
          "The project's Gradle version 8.0 is incompatible with the Gradle JVM version ${JdkConstants.JDK_21_VERSION} currently " +
          "selected to run Gradle build. Gradle 8.0 supports Java versions between 1.8 and 19. Please update the selected JVM " +
          "to a compatible version.")

        expect.that(buildIssue.quickFixes.map { it::class.java }).isEqualTo(listOf(
          UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix::class.java,
          SelectJdkFromFileSystemQuickFix::class.java
        ))
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP,
      expectedFailureDetailsString = null,
      expectedPhasesReported = null
    )
  }

  @Test
  fun `test Given project using an incompatible Daemon JVM criteria When sync Then expected build output exception is thrown`() {
    val preparedProject = projectRule.prepareTestProject(
      testProject = JdkTestProject.SimpleApplication(
        gradleDaemonToolchain = GradleDaemonToolchain(
          version = JdkConstants.JDK_11_VERSION,
          autoDetectionEnabled = true
        )
      ),
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_9_0
    )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = null,
      verifyBuildIssue = { _, buildIssue ->
        expect.that(buildIssue.title).contains("Incompatible Gradle JVM version")
        expect.that(buildIssue.description).contains(
          "The project's Gradle version 9.0.0 is incompatible with the Gradle JVM version ${JdkConstants.JDK_11_VERSION} currently " +
          "selected to run Gradle build. Gradle 9.0.0 supports Java versions between 17 and 24. Please update the selected JVM " +
          "to a compatible version.")

        expect.that(buildIssue.quickFixes.map { it::class.java }).isEqualTo(listOf(
          UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix::class.java,
          GradleOpenDaemonJvmSettingsQuickFix::class.java
        ))
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP,
      expectedFailureDetailsString = null,
      expectedPhasesReported = null
    )
  }
}