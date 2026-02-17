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
package com.android.tools.idea.gradle.project.sync.jdk.runsGradle

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.jdk.GradleJvmCompatibilityResolver
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants
import com.android.tools.idea.testing.TestMessagesDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GradleJvmCompatibilityCheckerTest {

  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  lateinit var testDialog: TestMessagesDialog

  @Before
  fun setUp() {
    StudioFlags.EXECUTE_GRADLE_JVM_COMPATIBILITY_CHECK.override(true)
    testDialog = TestMessagesDialog(0)
    TestDialogManager.setTestDialog(testDialog)
  }

  @After
  fun tearDown() {
    StudioFlags.EXECUTE_GRADLE_JVM_COMPATIBILITY_CHECK.clearOverride()
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun `test Given project with invalid Gradle JVM When importing project Then invalid JVM dialog is displayed updating JVM version`() {
    val preparedProject =
      projectRule.prepareTestProject(
        testProject = AndroidCoreTestProject.SIMPLE_APPLICATION,
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST,
      )

    preparedProject.open(updateOptions = { it.copy(overrideProjectGradleJdkPath = File(JdkConstants.JDK_11_PATH), verifyOpened = {}) }) {
      it.assertDialogUpdatesGradleJvmVersion()
    }
  }

  @Test
  fun `test Given already imported project with invalid Gradle JVM When reopen project Then invalid JVM dialog is displayed updating JVM version`() {
    val preparedProject =
      projectRule.prepareTestProject(
        testProject = AndroidCoreTestProject.SIMPLE_APPLICATION,
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST,
      )

    // Import project first time
    preparedProject.open {}

    // Reopen project
    preparedProject.open(updateOptions = { it.copy(overrideProjectGradleJdkPath = File(JdkConstants.JDK_11_PATH), verifyOpened = {}) }) {
      it.assertDialogUpdatesGradleJvmVersion()
    }
  }

  fun Project.assertDialogUpdatesGradleJvmVersion() {
    val gradleVersion = GradleVersion.version(SdkConstants.GRADLE_LATEST_VERSION)
    val gradleJvmCompatibility = GradleJvmCompatibilityResolver.resolve(this, gradleVersion)
    val expectedDialogMessage =
      "The project's Gradle version Gradle ${gradleVersion.version} is incompatible with the Gradle JVM version 11. To fix this, " +
        "select a JVM version that is at least ${gradleJvmCompatibility.minimumSupportedJavaVersion} and at most " +
        "${gradleJvmCompatibility.maximumSupportedJavaVersion}."
    assertEquals(expectedDialogMessage, testDialog.displayedMessage)

    runBlocking {
      val gradleJdk =
        AndroidStudioGradleInstallationManager.instance.resolveGradleJvmPath(this@assertDialogUpdatesGradleJvmVersion, basePath.orEmpty())
      assertEquals(JdkConstants.JDK_EMBEDDED_PATH, gradleJdk)
    }
  }
}
