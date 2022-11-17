/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.gradle.project.sync.constants.JDK_11
import com.android.tools.idea.gradle.project.sync.constants.JDK_11_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_17
import com.android.tools.idea.gradle.project.sync.constants.JDK_17_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_INVALID_PATH
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.DETACHED
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.INVALID
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
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
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_17 and not defined projectJdk When synced project successfully Then projectJdk is configured with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH)),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_17 and invalid projectJdk When synced project successfully Then projectJdk is configured with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH)),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_17 and projectJdk JDK_11 When synced project successfully Then projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_11, JDK_11_PATH),
          Jdk("another JDK_17", JDK_17_PATH),
          Jdk(JDK_17, JDK_17_PATH),
          Jdk("another JDK_17(2)", JDK_17_PATH)
        ),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk #JAVA_HOME pointing to JDK_17 and projectJdk JDK_17 When synced project successfully Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME,
        ideaProjectJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH), Jdk(JDK_17, JDK_17_PATH)),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 and not defined projectJdk When synced project successfully Then projectJdk is configured with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_17,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 and invalid projectJdk When synced project successfully Then projectJdk is configured with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_17,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 and projectJdk JDK_11 When synced project successfully Then projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17,
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_11, JDK_11_PATH),
          Jdk("another JDK_17", JDK_17_PATH),
          Jdk(JDK_17, JDK_17_PATH),
          Jdk("another JDK_17(2)", JDK_17_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_17,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk using non expected JDK_17 entry When synced project successfully Then projectJdk is updated with specific jdkTable entry created for JDK_17_PATH`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = "jdk entry 1",
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk("jdk entry 1", JDK_17_PATH),
          Jdk("jdk entry 2", JDK_17_PATH),
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = "jdk entry 1",
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk and projectJdk JDK_17 When synced project successfully Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17,
        ideaProjectJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH), Jdk(JDK_17, JDK_17_PATH))
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_17,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 with jdkTable entry but corrupted roots When synced project successfully Then jdkTable entry roots are fixed and projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME,
        ideaProjectJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH, rootsType = INVALID)),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 with jdkTable entry but no roots When synced project successfully Then jdkTable entry roots are fixed and projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = ExternalSystemJdkUtil.USE_JAVA_HOME,
        ideaProjectJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH, rootsType = DETACHED)),
        environmentVariables = mapOf(ExternalSystemJdkUtil.JAVA_HOME to JDK_17_PATH)
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = ExternalSystemJdkUtil.USE_JAVA_HOME,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = JDK_17_PATH
      )
    }

  @Test
  fun `Given gradleJdk JDK_17 with different path on jdkTable entry When synced project successfully Then projectJdk is updated always with jdk provider plus version without matter its path`() {
    val tmpJdkFolder = temporaryFolder.newFolder("tmp-jdk")
    FileUtil.copyDir(File(JDK_17_PATH), tmpJdkFolder)
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_17, tmpJdkFolder.path),
          Jdk("other", JDK_17_PATH)
        )
      )
    ) {
      syncWithAssertion(
        expectedGradleJdkName = JDK_17,
        expectedProjectJdkName = JDK_17,
        expectedJdkPath = tmpJdkFolder.path
      )
    }
  }

  @Test
  fun `Given gradleJdk JDK_17 with invalid jdkTable entry When sync project failed Then projectJdk isn't updated`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_17,
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_INVALID_PATH))
      )
    ) {
      sync(
        assertOnDiskConfig = { assertProjectJdk("any") },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }
}