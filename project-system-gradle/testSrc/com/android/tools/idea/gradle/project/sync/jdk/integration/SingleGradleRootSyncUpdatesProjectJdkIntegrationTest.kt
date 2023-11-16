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
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.DETACHED
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.INVALID
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_11
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@RunsInEdt
class SingleGradleRootSyncUpdatesProjectJdkIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_EMBEDDED and not defined projectJdk When synced project successfully Then projectJdk is configured with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH)),
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
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_EMBEDDED and invalid projectJdk When synced project successfully Then projectJdk is configured with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH)),
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
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_EMBEDDED and projectJdk JDK_11 When synced project successfully Then projectJdk is updated with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_11, JDK_11_PATH),
          Jdk("another JDK_EMBEDDED", JDK_EMBEDDED_PATH),
          Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          Jdk("another JDK_EMBEDDED(2)", JDK_EMBEDDED_PATH)
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
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_EMBEDDED and projectJdk JDK_EMBEDDED When synced project successfully Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH), Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH)),
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
  fun `Given gradleJdk JDK_EMBEDDED and not defined projectJdk When synced project successfully Then projectJdk is configured with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED
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
  fun `Given gradleJdk JDK_EMBEDDED and invalid projectJdk When synced project successfully Then projectJdk is configured with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = "any"
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
  fun `Given gradleJdk JDK_EMBEDDED and projectJdk JDK_11 When synced project successfully Then projectJdk is updated with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_11, JDK_11_PATH),
          Jdk("another JDK_EMBEDDED", JDK_EMBEDDED_PATH),
          Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH),
          Jdk("another JDK_EMBEDDED(2)", JDK_EMBEDDED_PATH)
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
  fun `Given gradleJdk using non expected JDK_EMBEDDED entry When synced project successfully Then projectJdk is updated with specific jdkTable entry created for JDK_EMBEDDED_PATH`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = "jdk entry 1",
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk("jdk entry 1", JDK_EMBEDDED_PATH),
          Jdk("jdk entry 2", JDK_EMBEDDED_PATH),
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = "jdk entry 1",
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk and projectJdk JDK_EMBEDDED When synced project successfully Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH), Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = JDK_EMBEDDED_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_EMBEDDED with jdkTable entry but corrupted roots When synced project successfully Then jdkTable entry roots are fixed and projectJdk is updated with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH, rootsType = INVALID)),
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
  fun `Given gradleJdk JDK_EMBEDDED with jdkTable entry but no roots When synced project successfully Then jdkTable entry roots are fixed and projectJdk is updated with JDK_EMBEDDED`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
        ideaProjectJdk = JDK_EMBEDDED
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_EMBEDDED_PATH, rootsType = DETACHED)),
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
  fun `Given gradleJdk JDK_EMBEDDED with different path on jdkTable entry When synced project successfully Then projectJdk is updated always with jdk provider plus version without matter its path`() {
    val tmpJdkFolder = temporaryFolder.newFolder("tmp-jdk")
    FileUtil.copyDir(File(JDK_EMBEDDED_PATH), tmpJdkFolder)
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_EMBEDDED, tmpJdkFolder.path),
          Jdk("other", JDK_EMBEDDED_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_EMBEDDED,
        expectedProjectJdkName = JDK_EMBEDDED,
        expectedProjectJdkPath = tmpJdkFolder.path
      )
    }
  }

  @Test
  fun `Given gradleJdk JDK_EMBEDDED with invalid jdkTable entry When sync project failed Then projectJdk isn't updated`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_EMBEDDED,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_EMBEDDED, JDK_INVALID_PATH))
      )
    ) {
      sync(
        assertOnDiskConfig = { assertProjectJdk("any") },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }
}