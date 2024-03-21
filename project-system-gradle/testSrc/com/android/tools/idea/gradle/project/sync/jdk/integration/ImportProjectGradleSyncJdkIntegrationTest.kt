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
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationWithoutIdea
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("UnstableApiUsage")
@RunsInEdt
@RunWith(Parameterized::class)
class ImportProjectGradleSyncJdkIntegrationTest(private val jdkVersion: Int) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "jdk{0}")
    fun data(): List<Int> {
      // Add additional jdk version to run the test against
      val jdkList = mutableListOf(17)
      val currentJdk = Runtime.version().feature()
      if (!jdkList.contains(currentJdk)) {
        jdkList.add(currentJdk)
      }
      return jdkList
    }
  }

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

  @Test
  @OldAgpTest(agpVersions = ["7.4.1"], gradleVersions = ["7.5"])
  fun `Given not configured project and valid pre-config JDK When import project Then was configured with #GRADLE_LOCAL_JAVA_HOME and user selected pre-config JDK`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_11_PATH
    jdkIntegrationTest.run(
      project = SimpleApplicationWithoutIdea(
        agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_74 // Later versions of AGP (8.0 and beyond) require JDK17
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_GRADLE_LOCAL_JAVA_HOME,
        expectedProjectJdkName = JDK_11,
        expectedProjectJdkPath = JDK_11_PATH
      )
    }
  }

  @Test
  fun `Given not configured project and invalid pre-config JDK When import project Then was configured with #GRADLE_LOCAL_JAVA_HOME and Embedded JDK`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_INVALID_PATH
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
}