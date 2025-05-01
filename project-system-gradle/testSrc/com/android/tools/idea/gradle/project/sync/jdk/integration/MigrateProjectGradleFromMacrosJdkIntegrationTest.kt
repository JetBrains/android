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
import com.android.tools.idea.gradle.project.sync.model.GradleDaemonToolchain
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationMultipleRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_11
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_VERSION
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
class MigrateProjectGradleFromMacrosJdkIntegrationTest {

  @get:Rule
  val separateOldAgpTestsRule = SeparateOldAgpTestsRule()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given not defined gradleJdk and projectJdk When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication()
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given not defined gradleJdk and projectJdk without table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaProjectJdk = JDK_EMBEDDED
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given not defined gradleJdk and projectJdk with invalid table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_INVALID_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given not defined gradleJdk and projectJdk with valid table entry When sync project Then gradleJdk was configured with projectJdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as '#USE_PROJECT_JDK' and daemon JVM criteria When sync project Then gradleJdk got unconfigured`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_PROJECT_JDK,
        gradleDaemonToolchain = GradleDaemonToolchain(JDK_EMBEDDED_VERSION)
      )
    ) {
      syncAssertingUndefinedGradleJdK()
    }

  @Test
  fun `Given gradleJdk as '#USE_PROJECT_JDK' and not defined gradleJdk and projectJdk When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_PROJECT_JDK
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as '#USE_PROJECT_JDK' and projectJdk without table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_PROJECT_JDK,
        ideaProjectJdk = JDK_11
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as '#USE_PROJECT_JDK' and projectJdk with invalid table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_PROJECT_JDK,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_INVALID_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk as '#USE_PROJECT_JDK' and projectJdk with valid table entry When sync project Then gradleJdk was configured with projectJdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_PROJECT_JDK,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH))
      ),
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_11,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }

  @Test
  fun `Given multiple roots project using non desired gradleJvm When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1"),
          GradleRoot("project_root2", USE_PROJECT_JDK)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          "project_root2" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH)
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given multiple roots project using non desired gradleJvm and projectJdk without table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        ideaProjectJdk = JDK_11,
        roots = listOf(
          GradleRoot("project_root1"),
          GradleRoot("project_root2", USE_PROJECT_JDK)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          "project_root2" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH)
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given multiple roots project using non desired gradleJvm and projectJdk with invalid table entry When sync project Then those were configured with Embedded Jdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        ideaProjectJdk = JDK_11,
        roots = listOf(
          GradleRoot("project_root1"),
          GradleRoot("project_root2", USE_PROJECT_JDK)
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_INVALID_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          "project_root2" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH)
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots project using non desired gradleJvm and projectJdk with valid table entry When sync project Then gradleJdk was configured with projectJdk`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaProjectJdk = JDK_11,
        roots = listOf(
          GradleRoot("project_root1"),
          GradleRoot("project_root2", USE_PROJECT_JDK)
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH))
      ),
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(JDK_11, JDK_11_PATH),
          "project_root2" to ExpectedGradleRoot(JDK_11, JDK_11_PATH)
        ),
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }
}