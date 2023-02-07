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
package com.android.tools.idea.gradle.project.sync.jdk.integration

import com.android.testutils.junit4.OldAgpTest
import com.android.testutils.junit4.SeparateOldAgpTestsRule
import com.android.tools.idea.testing.JdkConstants.JDK_11
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_17
import com.android.tools.idea.testing.JdkConstants.JDK_17_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.android.tools.idea.gradle.project.sync.model.ExpectedGradleRoot
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnstableApiUsage")
@RunsInEdt
class GradleSyncUseLocalJavaHomeIntegrationTest {

  @get:Rule
  val separateOldAgpTestsRule = SeparateOldAgpTestsRule()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test(expected = ExternalSystemJdkException::class)
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property When sync project Then throw exception`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME
      )
    ) {
      sync()
    }

  @Test(expected = ExternalSystemJdkException::class)
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with empty javaHome property When sync project Then throw exception`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHomePath = ""
      )
    ) {
      sync()
    }

  @Test(expected = ExternalSystemJdkException::class)
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with invalid javaHome property When sync project Then throw exception`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHomePath = JDK_INVALID_PATH
      )
    ) {
      sync()
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with valid javaHome property When sync project Then sync used the provided Jdk path`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHomePath = JDK_11_PATH
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given multiple roots project using hardcore gradleJvm naming When pre-sync project Then those were migrated away from hardcoded naming`() =
    jdkIntegrationTest.run(
      project = JdkTestProject.SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
          roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME, JDK_17_PATH),
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          ),
          "project_root2" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_17_PATH,
            gradleLocalJavaHome = JDK_17_PATH
          )
        ),
        expectedProjectJdkName = JDK_17,
        expectedProjectJdkPath = JDK_17_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given multiple roots project using hardcore gradleJvm naming When pre-sync project Then those were migrated away from hardcoded naming2`() =
    jdkIntegrationTest.run(
      project = JdkTestProject.SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
        )
      ),
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          ),
          "project_root2" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          )
        ),
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }
}
