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

import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationWithoutIdea
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnstableApiUsage")
@RunsInEdt
class ImportProjectWithoutIdeaJdkIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given not configured project When import project Then was configured with #GRADLE_LOCAL_JAVA_HOME and Embedded JDK`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationWithoutIdea()
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }
}