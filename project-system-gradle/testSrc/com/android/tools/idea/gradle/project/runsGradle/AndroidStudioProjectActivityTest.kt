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
package com.android.tools.idea.gradle.project.runsGradle

import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidStudioProjectActivityTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.Companion.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given invalid Daemon JVM criteria When import project Then project doesn't restore configuration`() =
    jdkIntegrationTest.run(
      project = JdkTestProject.SimpleApplication(
        gradleDaemonJvmCriteria = GradleDaemonJvmCriteria(
          version = "invalid",
          vendor = null
        ),
      ),
      environment = JdkIntegrationTest.TestEnvironment(
        studioFlags = JdkIntegrationTest.StudioFeatureFlags(restoreInvalidGradleJdkConfiguration = true)
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
  fun `Given invalid Gradle JDK configuration When import project Then project restore configuration`() =
    jdkIntegrationTest.run(
      project = JdkTestProject.SimpleApplication(
        ideaGradleJdk = "invalid"
      ),
      environment = JdkIntegrationTest.TestEnvironment(
        studioFlags = JdkIntegrationTest.StudioFeatureFlags(restoreInvalidGradleJdkConfiguration = true)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JdkConstants.JDK_EMBEDDED,
        expectedProjectJdkName = JdkConstants.JDK_EMBEDDED,
        expectedProjectJdkPath = JdkConstants.JDK_EMBEDDED_PATH
      )
    }
}