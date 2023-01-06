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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ThrowableConsumer
import com.intellij.util.ThrowableConvertor
import java.io.File
import java.time.Clock

/**
 * A test project definition that can be used in integration tests involving projects set up by Gradle.
 */
interface TestProjectDefinition {
  /**
   * A predicate which returns whether this test project is compatible with a given AGP software environment.
   */
  val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean

  /**
   * Prepares a test project defined by this instance for the given [agpVersion] in the given [integrationTestEnvironment] and returns
   * a handle to open/use the project in the test.
   */
  fun prepareTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    ndkVersion: String?
  ): PreparedTestProject


  companion object {
    @JvmStatic
    @JvmOverloads
    fun IntegrationTestEnvironment.prepareTestProject(
      testProject: TestProjectDefinition,
      name: String = "project",
      agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.selected,
      ndkVersion: String? = SdkConstants.NDK_DEFAULT_VERSION
    ): PreparedTestProject {
      return testProject.prepareTestProject(this, name, agpVersion, ndkVersion)
    }
  }
}

interface PreparedTestProject {
  interface Context {
    val project: Project
    val projectRoot: File
    val fixture: JavaCodeInsightTestFixture
    fun selectModule(module: Module)
  }

  fun <T> open(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions = { it },
    body: Context.(Project) -> T
  ): T

  fun open(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions = { it },
    body: ThrowableConsumer<Project, Exception>
  ) {
    return open(updateOptions, body = fun Context.(project: Project): Unit = body.consume(project))
  }

  fun <T> open(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions = { it },
    body: ThrowableConvertor<Project, T, Exception>
  ): T {
    return open(updateOptions, body = fun Context.(project: Project): T = body.convert(project))
  }

  val root: File

  companion object {
    @JvmStatic
    fun openPreparedTestProject(preparedProject: PreparedTestProject, body: ThrowableConsumer<Project, Exception>) {
      preparedProject.open(body = body)
    }

    @JvmStatic
    fun <T> openPreparedTestProject(preparedProject: PreparedTestProject, body: ThrowableConvertor<Project, T, Exception>) {
      preparedProject.open(body = body)
    }

    @JvmStatic
    fun <T> IntegrationTestEnvironment.openTestProject(testProject: TestProjectDefinition, body: Context.(Project) -> T) {
      // Since this method can be called multiple times and there is no reliable way to delete project directories on Windows and, moreover,
      // we do not want intelliJ's external system caches to be reused we need to name projects uniquely. However, we also want to have
      // a stable IDE project name, so we place them in a uniquely named parent directory.
      val preparedProject = prepareTestProject(testProject, name = "${Clock.systemUTC().millis()}/p")
      preparedProject.open(body = body)
    }

    @JvmStatic
    fun IntegrationTestEnvironment.openTestProject(testProject: TestProjectDefinition, body: ThrowableConsumer<Project, Exception>) {
      openTestProject(testProject) {
        body.consume(it)
      }
    }

    @JvmStatic
    fun <T> IntegrationTestEnvironment.openTestProject(
      testProject: TestProjectDefinition,
      body: ThrowableConvertor<Project, T, Exception>
    ) {
      return openTestProject(testProject) {
        body.convert(it)
      }
    }
  }
}

