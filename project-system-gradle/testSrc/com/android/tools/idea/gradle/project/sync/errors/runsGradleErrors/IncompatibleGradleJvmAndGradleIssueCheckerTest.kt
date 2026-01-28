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

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.testutils.junit4.OldAgpTest
import com.android.testutils.junit4.SeparateOldAgpTestsRule
import com.android.tools.idea.gradle.extensions.getRecommendedJavaVersion
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.model.GradleDaemonToolchain
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SUCCESS
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.JdkConstants
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.plugins.gradle.issue.quickfix.GradleOpenDaemonJvmSettingsQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class IncompatibleGradleJvmAndGradleIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @get:Rule val separateOldAgpTestsRule = SeparateOldAgpTestsRule()

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `test Given sync failure with incompatible Jvm When run quick fix Then project is configured with compatible Jvm and re-synced successfully`() {
    IdeSdks.getInstance().getOrCreateJdk(JdkConstants.JDK_17_PATH.asPath())

    val preparedProject =
      projectRule.prepareTestProject(
        testProject = AndroidCoreTestProject.SIMPLE_APPLICATION,
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_74,
      )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = File(JdkConstants.JDK_21_PATH),
      verifyBuildIssue = { project, buildIssue ->
        expect.that(buildIssue.title).contains("Incompatible Gradle JVM version")
        expect
          .that(buildIssue.description)
          .contains(
            "The project's Gradle version 7.5 is incompatible with the Gradle JVM version 21 currently selected to run Gradle build. " +
              "Gradle 7.5 supports Java versions between 1.8 and 18. Please update the selected JVM to a compatible version."
          )

        expect
          .that(buildIssue.quickFixes.map { it::class.java })
          .isEqualTo(
            listOf(UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix::class.java, SelectJdkFromFileSystemQuickFix::class.java)
          )

        // Execute quick-fix to apply compatible Gradle JDK configuration and sync
        val quickFix = buildIssue.quickFixes.first() as UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix
        PlatformTestUtil.waitForFuture(quickFix.runQuickFix(project, mock()))
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isSameAs(SUCCESS)

        runBlocking {
          val gradleJdk = AndroidStudioGradleInstallationManager.instance.resolveGradleJvmPath(project, project.basePath.orEmpty())
          expect.that(gradleJdk).isEqualTo(JdkConstants.JDK_17_PATH)
        }
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP,
      expectedFailureDetailsString = null,
      expectedPhasesReported = null,
    )
  }

  @Test
  fun `test Given old project using an incompatible newer JDK When sync fails with multiple exceptions Then those are consumed and expected build output exception is thrown`() {
    val preparedProject =
      projectRule.prepareTestProject(
        testProject = AndroidCoreTestProject.SIMPLE_APPLICATION,
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_73,
      )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = File(JdkConstants.JDK_21_PATH),
      verifyBuildIssue = { _, buildIssue ->
        expect.that(buildIssue.title).contains("Incompatible Gradle JVM version")
        expect
          .that(buildIssue.description)
          .contains(
            "The project's Gradle version 7.4 is incompatible with the Gradle JVM version 21 currently selected to run Gradle build. " +
              "Gradle 7.4 supports Java versions between 1.8 and 17. Please update the selected JVM to a compatible version."
          )

        expect
          .that(buildIssue.quickFixes.map { it::class.java })
          .isEqualTo(
            listOf(UpdateGradleJdkConfigurationCompatibleGradleVersionQuickFix::class.java, SelectJdkFromFileSystemQuickFix::class.java)
          )
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP,
      expectedFailureDetailsString = null,
      expectedPhasesReported = null,
    )
  }

  @Test
  fun `test Given sync failure with incompatible Jvm When run quick fix Then project using Daemon toolchain is configured with compatible Jvm and re-synced successfully`() {
    val gradleVersion = GradleVersion.version(GRADLE_LATEST_VERSION)
    val preparedProject =
      projectRule.prepareTestProject(
        testProject =
          JdkTestProject.SimpleApplication(
            gradleDaemonToolchain =
              GradleDaemonToolchain(version = JdkConstants.JDK_11_VERSION, autoDetectionEnabled = true, applyToolchainResolverPlugin = true)
          ),
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST,
      )

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = null,
      verifyBuildIssue = { project, buildIssue ->
        val expectedMinimumJavaVersion = GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion)
        val expectedMaximumJavaVersion = GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(gradleVersion)
        expect.that(buildIssue.title).contains("Incompatible Gradle JVM version")
        expect
          .that(buildIssue.description)
          .contains(
            "The project's Gradle version ${gradleVersion.version} is incompatible with the Gradle JVM version 11 currently selected to " +
              "run Gradle build. Gradle ${gradleVersion.version} supports Java versions between $expectedMinimumJavaVersion and " +
              "$expectedMaximumJavaVersion. Please update the selected JVM to a compatible version."
          )

        expect
          .that(buildIssue.quickFixes.map { it::class.java })
          .isEqualTo(
            listOf(UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix::class.java, GradleOpenDaemonJvmSettingsQuickFix::class.java)
          )

        // Execute quick-fix to apply compatible Gradle JVM criteria configuration and sync
        val quickFix = buildIssue.quickFixes.first() as UpdateDaemonJvmCriteriaCompatibleGradleVersionQuickFix
        PlatformTestUtil.waitForFuture(quickFix.runQuickFix(project, mock()))
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isSameAs(SUCCESS)

        val gradleJvmVersion = GradleDaemonJvmPropertiesFile.getProperties(project.basePath!!.asPath()).version?.value
        val expectedJvmVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(project, gradleVersion)
        expect.that(gradleJvmVersion).isEqualTo(expectedJvmVersion.toFeatureString())
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP,
      expectedFailureDetailsString = null,
      expectedPhasesReported = null,
    )
  }
}
