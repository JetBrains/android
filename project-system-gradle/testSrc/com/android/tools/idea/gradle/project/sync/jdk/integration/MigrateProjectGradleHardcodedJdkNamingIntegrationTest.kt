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

import com.android.tools.idea.gradle.project.sync.model.ExpectedGradleRoot
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationMultipleRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.ANDROID_STUDIO_DEFAULT_JDK_NAME
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.ANDROID_STUDIO_JAVA_HOME_NAME
import com.android.tools.idea.sdk.DefaultAndroidGradleJvmNames.EMBEDDED_JDK_NAME
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
class MigrateProjectGradleHardcodedJdkNamingIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given projectJdk as 'Embedded JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = EMBEDDED_JDK_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          Jdk(EMBEDDED_JDK_NAME, JDK_11_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as 'Embedded JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = EMBEDDED_JDK_NAME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(EMBEDDED_JDK_NAME, JDK_EMBEDDED_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk and projectJdk as 'Embedded JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = EMBEDDED_JDK_NAME,
        ideaProjectJdk = EMBEDDED_JDK_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(EMBEDDED_JDK_NAME, JDK_11_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk 'Embedded JDK' When pre-sync failure project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = EMBEDDED_JDK_NAME
      ),
    ) {
      sync(
        assertInMemoryConfig = { assertGradleJdk(JDK_EMBEDDED) },
        assertOnDiskConfig = { assertGradleJdk(JDK_EMBEDDED) },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }

  @Test
  fun `Given projectJdk as 'Android Studio java home' When pre-sync project Then this was migrated to #JAVA_HOME macro`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = ANDROID_STUDIO_JAVA_HOME_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          Jdk(EMBEDDED_JDK_NAME, JDK_11_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as 'Android Studio java home' When pre-sync project Then this was migrated to #JAVA_HOME macro`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_JAVA_HOME_NAME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(ANDROID_STUDIO_JAVA_HOME_NAME, JDK_EMBEDDED_PATH)
        ),
        environmentVariables = mapOf(JAVA_HOME to JDK_EMBEDDED_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk 'Android Studio java home' When pre-sync failure project Then this was migrated to #JAVA_HOME macro`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_JAVA_HOME_NAME
      ),
    ) {
      sync(
        assertInMemoryConfig = { assertGradleJdk(USE_JAVA_HOME) },
        assertOnDiskConfig = { assertGradleJdk(USE_JAVA_HOME) },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }

  @Test
  fun `Given gradleJdk and projectJdk as 'Android Studio java home' When pre-sync project Then this was migrated to #JAVA_HOME macro`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_JAVA_HOME_NAME,
        ideaProjectJdk = ANDROID_STUDIO_JAVA_HOME_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(ANDROID_STUDIO_JAVA_HOME_NAME, JDK_EMBEDDED_PATH)),
        environmentVariables = mapOf(JAVA_HOME to JDK_EMBEDDED_PATH)
      ),
    ) {
      syncWithAssertion(
        expectedGradleJdkName = USE_JAVA_HOME,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given projectJdk as 'Android Studio default JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = ANDROID_STUDIO_DEFAULT_JDK_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          Jdk(ANDROID_STUDIO_DEFAULT_JDK_NAME, JDK_11_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk as 'Android Studio default JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_DEFAULT_JDK_NAME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(ANDROID_STUDIO_DEFAULT_JDK_NAME, JDK_EMBEDDED_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk and projectJdk as 'Android Studio default JDK' When pre-sync project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_DEFAULT_JDK_NAME,
        ideaProjectJdk = ANDROID_STUDIO_DEFAULT_JDK_NAME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(ANDROID_STUDIO_DEFAULT_JDK_NAME, JDK_11_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk 'Android Studio default JDK' When pre-sync failure project Then this was migrated to vendor plus version JDK naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ANDROID_STUDIO_DEFAULT_JDK_NAME
      ),
    ) {
      sync(
        assertInMemoryConfig = { assertGradleJdk(JDK_EMBEDDED) },
        assertOnDiskConfig = { assertGradleJdk(JDK_EMBEDDED) },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }

  @Test
  fun `Given multiple roots project using hardcore gradleJvm naming When pre-sync project Then those were migrated away from hardcoded naming`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", EMBEDDED_JDK_NAME),
          GradleRoot("project_root2", ANDROID_STUDIO_JAVA_HOME_NAME),
          GradleRoot("project_root3", ANDROID_STUDIO_DEFAULT_JDK_NAME)
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(EMBEDDED_JDK_NAME, JDK_EMBEDDED_PATH),
          Jdk(ANDROID_STUDIO_JAVA_HOME_NAME, JDK_11_PATH),
          Jdk(ANDROID_STUDIO_DEFAULT_JDK_NAME, JDK_11_PATH),
        ),
        environmentVariables = mapOf(JAVA_HOME to JDK_EMBEDDED_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "project_root1" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          "project_root2" to ExpectedGradleRoot(USE_JAVA_HOME, JDK_EMBEDDED_PATH),
          "project_root3" to ExpectedGradleRoot(JDK_EMBEDDED, JDK_EMBEDDED_PATH)
        ),
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }
}