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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.assertions.AssertInMemoryConfig
import com.android.tools.idea.gradle.project.sync.assertions.AssertOnDiskConfig
import com.android.tools.idea.gradle.project.sync.assertions.AssertOnFailure
import com.android.tools.idea.gradle.project.sync.assertions.AssertSyncEvents
import com.android.tools.idea.gradle.project.sync.model.ExpectedGradleRoot
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.utils.EnvironmentUtils
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.reflect.KClass

class JdkIntegrationTest(
  private val projectRule: IntegrationTestEnvironmentRule,
  private val temporaryFolder: TemporaryFolder,
  private val expect: Expect
) {

  fun run(
    project: JdkTestProject,
    environment: TestEnvironment = TestEnvironment(),
    body: ProjectRunnable.() -> Unit
  ) {
    val preparedProject = projectRule.prepareTestProject(
      agpVersion = project.agpVersion,
      name = project.name,
      testProject = project,
    )
    prepareTestEnvironment(
      testEnvironment = environment,
      disposable = projectRule.testRootDisposable,
      tempDir = temporaryFolder.newFolder()
    )

    try {
      body(ProjectRunnable(
        expect = expect,
        preparedProject = preparedProject
      ))
    } finally {
      cleanTestEnvironment()
    }
  }

  private fun prepareTestEnvironment(
    testEnvironment: TestEnvironment,
    disposable: Disposable,
    tempDir: File
  ) {
    JdkTableUtils.removeAllJavaSdkFromJdkTable()
    testEnvironment.run {
      userHomeGradlePropertiesJdkPath?.let {
        ProjectJdkUtils.setUserHomeGradlePropertiesJdk(it, disposable)
      }
      StudioFlags.MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME.override(studioFlags.migrateToGradleLocalJavaHome)
      JdkTableUtils.populateJdkTableWith(jdkTable, tempDir)
      EnvironmentUtils.overrideEnvironmentVariables(environmentVariables, disposable)
    }
  }

  private fun cleanTestEnvironment() {
    StudioFlags.MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME.clearOverride()
    JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
  }

  data class TestEnvironment(
    val userHomeGradlePropertiesJdkPath: String? = null,
    val environmentVariables: Map<String, String?> = mapOf(),
    val jdkTable: List<JdkTableUtils.Jdk> = emptyList(),
    val studioFlags: StudioFeatureFlags = StudioFeatureFlags()
  )

  data class StudioFeatureFlags(
    val migrateToGradleLocalJavaHome: Boolean = false
  )

  class ProjectRunnable(
    private val expect: Expect,
    private val preparedProject: PreparedTestProject
  ) {
    fun sync(
      assertInMemoryConfig: AssertInMemoryConfig.() -> Unit = {},
      assertOnDiskConfig: AssertOnDiskConfig.() -> Unit = {},
      assertOnFailure: AssertOnFailure.(Exception) -> Unit = { throw it },
      assertSyncEvents: AssertSyncEvents.() -> Unit = {},
    ) {
      var capturedException: Exception? = null
      val project = preparedProject.open(
        updateOptions = {
          it.copy(
            overrideProjectGradleJdkPath = null,
            syncExceptionHandler = { exception ->
              capturedException = exception
            },
            syncViewEventHandler = { event ->
              when (event) {
                is FinishBuildEvent -> {
                  val capturedExceptionSyncMessages = mutableListOf<String>()
                  (event.result as? FailureResult)?.failures?.forEach { failure ->
                    failure.message?.let { failureMessage ->
                      capturedExceptionSyncMessages.add(failureMessage)
                    }
                  }
                  assertSyncEvents(AssertSyncEvents(capturedExceptionSyncMessages, expect))
                }
              }
            },
            verifyOpened = {
              capturedException?.let { exception ->
                assertOnFailure(AssertOnFailure(exception, expect), exception)
              }
            }
          )
        }) { project ->
        assertInMemoryConfig(AssertInMemoryConfig(project, expect))
        project
      }
      assertOnDiskConfig(AssertOnDiskConfig(project, expect))
    }

    fun syncWithAssertion(
      expectedGradleJdkName: String,
      expectedProjectJdkName: String,
      expectedProjectJdkPath: String,
      expectedGradleLocalJavaHome: String? = null,
      expectedException: KClass<out Exception>? = null,
    ) {
      syncWithAssertion(
        expectedGradleRoots = mapOf(
          "" to ExpectedGradleRoot(
            ideaGradleJdk = expectedGradleJdkName,
            gradleExecutionDaemonJdkPath = expectedProjectJdkPath,
            gradleLocalJavaHome = expectedGradleLocalJavaHome
          )
        ),
        expectedProjectJdkName = expectedProjectJdkName,
        expectedProjectJdkPath = expectedProjectJdkPath,
        expectedException = expectedException
      )
    }

    fun syncWithAssertion(
      expectedGradleRoots: Map<String, ExpectedGradleRoot>,
      expectedProjectJdkName: String,
      expectedProjectJdkPath: String,
      expectedException: KClass<out Exception>? = null,
    ) {
      sync(
        assertInMemoryConfig = {
          assertGradleRoots(expectedGradleRoots)
          assertProjectJdk(expectedProjectJdkName)
          assertProjectJdkTablePath(expectedProjectJdkPath)
          assertProjectJdkTableEntryIsValid(expectedProjectJdkName)
        },
        assertOnDiskConfig = {
          assertGradleRoots(expectedGradleRoots)
          assertProjectJdk(expectedProjectJdkName)
        },
        assertOnFailure = { syncException ->
          expectedException?.let {
            assertException(it)
          } ?: run {
            throw syncException
          }
        }
      )
    }
  }
}