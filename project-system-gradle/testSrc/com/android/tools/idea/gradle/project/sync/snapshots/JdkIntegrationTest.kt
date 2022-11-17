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

import com.android.tools.idea.gradle.project.sync.assertions.AssertInMemoryConfig
import com.android.tools.idea.gradle.project.sync.assertions.AssertOnDiskConfig
import com.android.tools.idea.gradle.project.sync.assertions.AssertOnFailure
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.android.tools.idea.gradle.project.sync.utils.environment.TestEnvironment
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.testFramework.replaceService
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.reflect.KClass

class JdkIntegrationTest(
  private val projectRule: IntegrationTestEnvironmentRule,
  private val temporaryFolder: TemporaryFolder,
  private val expect: Expect
) {

  fun run(
    project: JdkTestProject = JdkTestProject.SIMPLE_APPLICATION,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    body: ProjectRunnable.() -> Unit
  ) {
    val preparedProject = projectRule.prepareTestProject(
      testProject = project,
      agpVersion = agpVersion
    )
    val environment = TestEnvironment()
    ApplicationManager.getApplication().replaceService(Environment::class.java, environment, projectRule.testRootDisposable)
    JdkTableUtils.removeAllJavaSdkFromJdkTable()

    body(ProjectRunnable(
      environment = environment,
      expect = expect,
      tempDir = temporaryFolder.newFolder(),
      preparedProject = preparedProject,
      projectJdkUtils = ProjectJdkUtils(
        disposable = projectRule.testRootDisposable,
        projectPath = preparedProject.root.toPath(),
        projectModules = project.projectModules
      )
    ))
  }

  class ProjectRunnable(
    private val environment: TestEnvironment,
    private val expect: Expect,
    private val preparedProject: PreparedTestProject,
    private val projectJdkUtils: ProjectJdkUtils,
    private val tempDir: File
  ) {
    fun configEnvironment(
      ideaGradleJdk: String? = null,
      ideaProjectJdk: String? = null,
      gradlePropertiesProjectJdkPath: String? = null,
      gradlePropertiesUserHomeJdkPath: String? = null,
      environmentVariables: Map<String, String?> = mapOf(),
      jdkTable: JdkTableUtils.Jdk
    ) {
      configEnvironment(
        ideaGradleJdk,
        ideaProjectJdk,
        gradlePropertiesProjectJdkPath,
        gradlePropertiesUserHomeJdkPath,
        environmentVariables,
        listOf(jdkTable)
      )
    }

    fun configEnvironment(
      ideaGradleJdk: String? = null,
      ideaProjectJdk: String? = null,
      gradlePropertiesProjectJdkPath: String? = null,
      gradlePropertiesUserHomeJdkPath: String? = null,
      environmentVariables: Map<String, String?> = mapOf(),
      jdkTable: List<JdkTableUtils.Jdk> = emptyList(),
    ) {
      ideaGradleJdk?.let {
        projectJdkUtils.setProjectIdeaGradleJdk(it)
      }
      ideaProjectJdk?.let {
        projectJdkUtils.setProjectIdeaJdk(it)
      }
      gradlePropertiesProjectJdkPath?.let {
        projectJdkUtils.setProjectGradlePropertiesJdk(it)
      }
      gradlePropertiesUserHomeJdkPath?.let {
        projectJdkUtils.setUserHomeGradlePropertiesJdk(it)
      }
      JdkTableUtils.populateJdkTableWith(jdkTable, tempDir)
      environment.variables(*environmentVariables.toList().toTypedArray())
    }

    fun sync(
      assertInMemoryConfig: AssertInMemoryConfig.() -> Unit = {},
      assertOnDiskConfig: AssertOnDiskConfig.() -> Unit = {},
      assertOnFailure: AssertOnFailure.(Exception) -> Unit = { throw it },
    ) {
      var capturedException: Exception? = null
      preparedProject.open(
        updateOptions = {
          it.copy(
            syncExceptionHandler = { exception ->
              capturedException = exception
            },
            verifyOpened = {
              capturedException?.let { exception ->
                assertOnFailure(AssertOnFailure(exception, expect), exception)
              }
            }
          )
        }) { project ->
        assertInMemoryConfig(AssertInMemoryConfig(project, projectJdkUtils, expect))
      }
      assertOnDiskConfig(AssertOnDiskConfig(projectJdkUtils, expect))
    }

    fun syncWithAssertion(
      expectedGradleJdkName: String,
      expectedProjectJdkName: String,
      expectedJdkPath: String,
      expectedException: KClass<out Exception>? = null,
    ) {
      sync(
        assertInMemoryConfig = {
          assertGradleExecutionDaemon(expectedJdkPath)
          assertGradleJdk(expectedGradleJdkName)
          assertProjectJdk(expectedProjectJdkName)
          assertProjectJdkTablePath(expectedJdkPath)
          assertProjectJdkTableEntryIsValid(expectedProjectJdkName)
        },
        assertOnDiskConfig = {
          assertGradleJdk(expectedGradleJdkName)
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