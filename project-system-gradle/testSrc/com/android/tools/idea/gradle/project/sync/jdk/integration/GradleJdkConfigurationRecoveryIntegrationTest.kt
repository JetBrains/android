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
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.StudioFeatureFlags
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
@Suppress("UnstableApiUsage")
class GradleJdkConfigurationRecoveryIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given project without gradleJvm and projectJdk When import project Then invalid configuration was restored with Embedded JDK`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(
          restoreInvalidGradleJdkConfiguration = true
        )
      )
    ) {
      skipSyncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedGradleJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given project with USE_PROJECT_JDK as gradleJvm and without projectJdk When import project Then invalid configuration was restored with Embedded JDK`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_PROJECT_JDK
      ),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(
          restoreInvalidGradleJdkConfiguration = true
        )
      )
    ) {
      skipSyncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedGradleJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given project without gradleJvm and invalid projectJdk When import project Then invalid configuration was restored with Embedded JDK`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaProjectJdk = "invalid"
      ),
      environment = TestEnvironment(
        studioFlags = StudioFeatureFlags(
          restoreInvalidGradleJdkConfiguration = true
        )
      )
    ) {
      skipSyncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedGradleJdkPath = JDK_EMBEDDED_PATH
      )
    }
}