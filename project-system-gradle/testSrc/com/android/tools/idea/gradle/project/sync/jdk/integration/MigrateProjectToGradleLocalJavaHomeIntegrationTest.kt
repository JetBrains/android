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
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.StudioFeatureFlags
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationMultipleRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_11
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
@Suppress("UnstableApiUsage")
class MigrateProjectToGradleLocalJavaHomeIntegrationTest {

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
  fun `Given invalid Gradle JDK configuration When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = "any"
      )
    ) {
      sync(
        assertOnDiskConfig = {
          assertGradleJdk(USE_JAVA_HOME)
          assertProjectJdk("any")
          assertGradleLocalJavaHome(null)
        },
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        }
      )
    }

  @Test
  fun `Given gradleJdk #GRADLE_LOCAL_JAVA_HOME pointing to valid JDK When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = JDK_EMBEDDED_PATH
      ),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = JDK_EMBEDDED_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk #JAVA_HOME pointing to valid JDK When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_JAVA_HOME
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JAVA_HOME to JDK_11_PATH),
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = null
      )
    }

  @Test
  fun `Given gradleJdk #JAVA_INTERNAL pointing to valid JDK When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_INTERNAL_JAVA
      ),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_INTERNAL_JAVA,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = null
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk #GRADLE_JAVA_HOME pointing to valid JDK When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_GRADLE_JAVA_HOME,
        gradlePropertiesJavaHome = JDK_11_PATH
      ),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = null
      )
    }

  @Test
  fun `Given gradleJdk STUDIO_GRADLE_JDK pointing to valid JDK When sync project Then no migration happened`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH)),
        environmentVariables = mapOf(IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME to JDK_EMBEDDED_PATH),
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = null
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk pointing to a valid jdkTable entry When sync project Then JDK config was migrated to Gradle local javaHome`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = "valid-entry"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("valid-entry", JDK_11_PATH)),
        studioFlags = StudioFeatureFlags(true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = JDK_11_PATH
      )
    }

  @Ignore("Flaky test: b/355740346")
  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given multiple roots with different JDK configuration When sync project Then only expected JDK config was migrated to Gradle local javaHome`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        roots = listOf(
          GradleRoot("project_root1", ideaGradleJdk = "valid-entry"),
          GradleRoot("project_root2", ideaGradleJdk = USE_JAVA_HOME),
          GradleRoot("project_root3", ideaGradleJdk = USE_INTERNAL_JAVA),
          GradleRoot("project_root4", ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME, gradleLocalJavaHome = JDK_11_PATH),
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("valid-entry", JDK_EMBEDDED_PATH)),
        environmentVariables = mapOf(JAVA_HOME to JDK_11_PATH),
        studioFlags = StudioFeatureFlags(true)
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
            ideaGradleJdk = USE_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = null
          ),
          "project_root3" to ExpectedGradleRoot(
            ideaGradleJdk = USE_INTERNAL_JAVA,
            gradleExecutionDaemonJdkPath = JDK_EMBEDDED_PATH,
            gradleLocalJavaHome = null
          ),
          "project_root4" to ExpectedGradleRoot(
            ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
            gradleExecutionDaemonJdkPath = JDK_11_PATH,
            gradleLocalJavaHome = JDK_11_PATH
          ),
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }
}