/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.testutils.junit4.SeparateOldAgpTestsRule
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_VERSION
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
class SingleGradleRootSyncUseDaemonJvmCriteriaIntegrationTest {

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
  fun `Given invalid Daemon Jvm criteria and valid Gradle JDK configuration When import project Then sync fails prioritizing criteria`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        gradleDaemonJvmCriteria = GradleDaemonJvmCriteria(
          version = "invalid",
          vendor = null
        ),
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JAVA_HOME to JDK_EMBEDDED_PATH)
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(BuildIssueException::class, "Value 'invalid' given for toolchainVersion is an invalid Java version")
        },
        assertSyncEvents = {
          assertExceptionMessage("Invalid Gradle Daemon JVM Criteria")
        }
      )
    }

  @Test
  fun `Given valid Daemon Jvm criteria using JDK_EMBEDDED version When import project Then projectJdk is configured with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        gradleDaemonJvmCriteria = GradleDaemonJvmCriteria(
          version = JDK_EMBEDDED_VERSION,
          vendor = null
        ),
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }
}