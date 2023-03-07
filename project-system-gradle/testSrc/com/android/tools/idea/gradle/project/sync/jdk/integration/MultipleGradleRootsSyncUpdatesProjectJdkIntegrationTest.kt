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
import com.android.tools.idea.gradle.project.sync.constants.JDK_11
import com.android.tools.idea.gradle.project.sync.constants.JDK_11_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_17
import com.android.tools.idea.gradle.project.sync.constants.JDK_17_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_INVALID_PATH
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplicationMultipleRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@RunsInEdt
class MultipleGradleRootsSyncUpdatesProjectJdkIntegrationTest {

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
  fun `Given multiple roots with gradleJdk JDK_17 When synced project successfully Then projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_17),
          GradleRoot("project_root2", JDK_17)
        ),
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH))
      )
    ) {
      sync(
        assertInMemoryConfig = { assertProjectJdkAndValidateTableEntry(JDK_17, JDK_17_PATH) },
        assertOnDiskConfig = { assertProjectJdk(JDK_17) }
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given root using gradleJdk #JAVA_HOME pointing to JDK_17 When synced project successfully Then projectJdk is updated with JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_11),
          GradleRoot("project_root2", USE_JAVA_HOME)
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_11, JDK_11_PATH)),
        environmentVariables = mapOf(JAVA_HOME to JDK_17_PATH)
      ),
      agpVersion = AGP_74 // Later versions of AGP (8.0 and beyond) require JDK17
    ) {
      sync(
        assertInMemoryConfig = { assertProjectJdkAndValidateTableEntry(JDK_17, JDK_17_PATH) },
        assertOnDiskConfig = { assertProjectJdk(JDK_17) }
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given multiple roots using different gradleJdk versions When synced project successfully Then projectJdk is updated with greatest JDK version JDK_17`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_11),
          GradleRoot("project_root2", JDK_11),
          GradleRoot("project_root3", JDK_17),
          GradleRoot("project_root4", JDK_11)
        ),
        ideaProjectJdk = JDK_11
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_17, JDK_17_PATH),
          Jdk(JDK_11, JDK_11_PATH)
        )
      ),
      agpVersion = AGP_74 // Later versions of AGP (8.0 and beyond) require JDK17
    ) {
      sync(
        assertInMemoryConfig = { assertProjectJdkAndValidateTableEntry(JDK_17, JDK_17_PATH) },
        assertOnDiskConfig = { assertProjectJdk(JDK_17) }
      )
    }

  @Test
  fun `Given multiple roots using non expected JDK_17 entry When synced project successfully Then projectJdk is updated with specific jdkTable entry created for JDK_17_PATH`() {
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", "jdkRoot1"),
          GradleRoot("project_root2", "jdkRoot2"),
          GradleRoot("project_root3", "jdkRoot3")
        )
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk("jdkRoot1", JDK_17_PATH),
          Jdk("jdkRoot2", JDK_17_PATH),
          Jdk("jdkRoot3", JDK_17_PATH)
        )
      )
    ) {
      sync(
        assertInMemoryConfig = { assertProjectJdkAndValidateTableEntry(JDK_17, JDK_17_PATH) },
        assertOnDiskConfig = { assertProjectJdk(JDK_17) }
      )
    }
  }

  @Test
  fun `Given multiple roots with projectJdk pointing to JDK_17 When synced project successfully Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_17),
          GradleRoot("project_root2", JDK_17)
        ),
        ideaProjectJdk = JDK_17
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk(JDK_17, JDK_17_PATH))
      )
    ) {
      sync(
        assertInMemoryConfig = { assertProjectJdkAndValidateTableEntry(JDK_17, JDK_17_PATH) },
        assertOnDiskConfig = { assertProjectJdk(JDK_17) }
      )
    }

  @Test
  fun `Given multiple roots with invalid jdkTable entry When sync project failed Then projectJdk isn't modified`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_17),
          GradleRoot("project_root2", JDK_17)
        ),
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_17, JDK_INVALID_PATH),
        )
      )
    ) {
      sync(
        assertOnDiskConfig = { assertProjectJdk("any") },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }

  @Test
  @OldAgpTest(agpVersions = ["7.4.0"], gradleVersions = ["7.5"])
  fun `Given multiple roots with invalid and valid jdkTable entry When sync partially succeed Then projectJdk is updated with greatest JDK synced version JDK_11`() =
    jdkIntegrationTest.run(
      project = SimpleApplicationMultipleRoots(
        roots = listOf(
          GradleRoot("project_root1", JDK_17),
          GradleRoot("project_root2", JDK_11)
        ),
        ideaProjectJdk = "any"
      ),
      environment = TestEnvironment(
        jdkTable = listOf(
          Jdk(JDK_17, JDK_INVALID_PATH),
          Jdk(JDK_11, JDK_11_PATH)
        )
      ),
      agpVersion = AGP_74 // Later versions of AGP (8.0 and beyond) require JDK17
    ) {
      sync(
        assertOnDiskConfig = { assertProjectJdk(JDK_11) },
        assertOnFailure = { assertException(ExternalSystemJdkException::class) }
      )
    }
}