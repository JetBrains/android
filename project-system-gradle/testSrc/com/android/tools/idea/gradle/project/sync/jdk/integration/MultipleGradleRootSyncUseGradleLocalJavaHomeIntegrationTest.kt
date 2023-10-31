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
import com.android.tools.idea.gradle.project.sync.model.ExpectedGradleRoot
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationMultipleRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_11
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnstableApiUsage")
@RunsInEdt
class MultipleGradleRootSyncUseGradleLocalJavaHomeIntegrationTest {

  @get:Rule
  val separateOldAgpTestsRule = SeparateOldAgpTestsRule()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @After
  fun tearDown() {
    GradleDefaultJdkPathStore.jdkPath = null
  }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and valid default Jdk When sync project Then sync used the default Jdk path`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_11_PATH
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME),
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

  @Test
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and invalid default Jdk When sync project Then sync used Embedded Jdk path`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_INVALID_PATH
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME),
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          ),
          "project_root2" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          )
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }
  }

  @Test
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk without table entry When sync project Then sync used Embedded Jdk path`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME),
        ),
        ideaProjectJdk = "jdk-no-table-entry"
      ),
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          ),
          "project_root2" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          )
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk with invalid table entry When sync project Then sync used Embedded Jdk path`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME),
        ),
        ideaProjectJdk = "jdk-invalid-table-entry"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("jdk-invalid-table-entry", JDK_INVALID_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          ),
          "project_root2" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          )
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk with valid table entry When sync project Then sync used project Jdk path`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME),
        ),
        ideaProjectJdk = "jdk-valid-table-entry"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("jdk-valid-table-entry", JDK_11_PATH))
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
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          )
        ),
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME with different valid javaHome When sync project Then project Jdk is using the highest version`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME, JDK_EMBEDDED_PATH),
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
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = JDK_EMBEDDED_PATH
          )
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots gradleJdk GRADLE_LOCAL_JAVA_HOME with same valid javaHome When sync project Then project Jdk is using the same version`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
          GradleRoot("project_root2", USE_GRADLE_LOCAL_JAVA_HOME, JDK_11_PATH),
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
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          )
        ),
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }
}