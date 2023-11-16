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
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
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
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnstableApiUsage")
@RunsInEdt
class SingleGradleRootSyncUseGradleLocalJavaHomeIntegrationTest {

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

  @Test(expected = ExternalSystemJdkException::class)
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with empty javaHome property When sync project Then throw exception`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = ""
      )
    ) {
      sync()
    }

  @Test(expected = ExternalSystemJdkException::class)
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with invalid javaHome property When sync project Then throw exception`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = JDK_INVALID_PATH
      )
    ) {
      sync()
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with valid javaHome property When sync project Then sync used the provided Jdk path`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = JDK_11_PATH
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = JDK_11_PATH
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and valid default Jdk When sync project Then sync used the default Jdk path`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_11_PATH
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = JDK_11_PATH
      )
    }
  }

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and invalid default Jdk When sync project Then sync used Embedded Jdk path`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_INVALID_PATH
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = JDK_EMBEDDED_PATH
      )
    }
  }

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property When sync project Then sync used Embedded Jdk path`() {
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = JDK_EMBEDDED_PATH
      )
    }
  }

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk without table entry When sync project Then sync used Embedded Jdk path`() {
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        ideaProjectJdk = "jdk-no-table-entry"
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = JDK_EMBEDDED_PATH
      )
    }
  }

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk with invalid table entry When sync project Then sync used Embedded Jdk path`() {
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        ideaProjectJdk = "jdk-invalid-table-entry"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("jdk-invalid-table-entry", JDK_INVALID_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH,
        expectedGradleLocalJavaHome = JDK_EMBEDDED_PATH
      )
    }
  }

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME without javaHome property and project Jdk with valid table entry When sync project Then sync used project Jdk path`() {
    jdkIntegrationTest.run(
      project = SimpleApplication(
        agpVersion = AGP_74, // Later versions of AGP (8.0 and beyond) require JDK17
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        ideaProjectJdk = "jdk-valid-table-entry"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("jdk-valid-table-entry", JDK_11_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH,
        expectedGradleLocalJavaHome = JDK_11_PATH
      )
    }
  }
}