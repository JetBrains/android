/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.SdkConstants
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

/**
 * Integration tests for [GradleProjectSystem]; contains tests that require a working gradle project.
 */
class GradleProjectSystemIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  @get:Rule
  val expect: Expect = Expect.create()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()

  @Test
  fun testGetDependentLibraries() {
    runTestOn(TestProjectPaths.SIMPLE_APPLICATION) { project ->
      val moduleSystem = project
        .getProjectSystem()
        .getModuleSystem(project.gradleModule(":app")!!)
      val libraries = moduleSystem.getAndroidLibraryDependencies()

      val appcompat = libraries
        .first { library -> library.address.startsWith("com.android.support:support-compat") }

      assertThat(appcompat.address).matches("com.android.support:support-compat:[\\.\\d]+@aar")
      assertThat(appcompat.manifestFile?.fileName).isEqualTo(SdkConstants.FN_ANDROID_MANIFEST_XML)
      assertThat(appcompat.resFolder!!.root.toFile()).isDirectory()
    }
  }

  @Test
  fun testGetDefaultApkFile() {
    runTestOn(TestProjectPaths.SIMPLE_APPLICATION) { project ->
      // Invoke assemble task to generate output listing file and apk file.
      project.buildAndWait { invoker -> invoker.assemble(arrayOf(project.gradleModule(":app")!!), TestCompileType.NONE) }
      val defaultApkFile = project
        .getProjectSystem()
        .getDefaultApkFile()
      assertThat(defaultApkFile).isNotNull()
      assertThat(defaultApkFile!!.name).isEqualTo("app-debug.apk")
    }
  }

  @Test
  fun testGetPackageName() {
    // `getPackageName` returns an `R` class Java package name and does not depend on the currently selected build variant or
    // whether the project has been built or not.

    fun Project.appModule() = this.gradleModule(":app")!!
    fun Project.appModuleSystem() = this.appModule().getModuleSystem()
    fun AssembleInvocationResult.errors() = this.invocationResult.invocations.mapNotNull { it.buildError }

    runTestOn(TestProjectPaths.APPLICATION_ID_SUFFIX) { project ->
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      // TODO(b/187710826): expect.that(project.appModuleSystem().getTestPackageName()).isEqualTo("one.name.test")

      val debugBuildResult = project.buildAndWait { it.assemble(arrayOf(project.appModule()), TestCompileType.NONE) }
      expect.that(debugBuildResult.errors()).isEmpty()
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      // TODO(b/187710826): expect.that(project.appModuleSystem().getTestPackageName()).isEqualTo("one.name.test")

      switchVariant(project, ":app", "release")
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      // TODO(b/187710826): expect.that(project.appModuleSystem().getTestPackageName()).isEqualTo("one.name.test")

      val releaseBuildResult = project.buildAndWait { it.assemble(arrayOf(project.appModule()), TestCompileType.NONE) }
      expect.that(releaseBuildResult.errors()).isEmpty()
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      // TODO(b/187710826): expect.that(project.appModuleSystem().getTestPackageName()).isEqualTo("one.name.test")
    }
  }

  private fun runTestOn(testProjectPath: String, test: (Project) -> Unit) {
    prepareGradleProject(
      testProjectPath,
      "project",
      gradleVersion = null,
      gradlePluginVersion = null,
      kotlinVersion = null
    )
    openPreparedProject("project", test)
  }
}
