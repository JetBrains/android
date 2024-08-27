/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.runsGradleProjectsystem

import com.android.SdkConstants
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.outputCurrentlyRunningTest
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration tests for [GradleProjectSystem]; contains tests that require a working gradle project.
 */
abstract class GradleProjectSystemIntegrationTestCase {

  @RunWith(Parameterized::class)
  class CurrentAgp : GradleProjectSystemIntegrationTestCase() {

    companion object {
      @Suppress("unused")
      @Contract(pure = true)
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun tests(): Collection<*> {
        return tests.filter{ it.modelsV2 }.map { listOf(it).toTypedArray() }
      }
    }
  }

  companion object {
    val tests =
      listOf(
        TestDefinition(agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT, modelsV2 = false),
        TestDefinition(agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT, modelsV2 = true)
      )
  }

  data class TestDefinition(
    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    val modelsV2: Boolean = false
  ) : AgpIntegrationTestDefinition {
    override val name: String = ""
    override fun toString(): String = displayName()
    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDefinition = copy(agpVersion = agpVersion)
  }

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testGetDependentLibraries() {
    runTestOn(TestProject.SIMPLE_APPLICATION) { project ->
      val moduleSystem = project
        .getProjectSystem()
        .getModuleSystem(project.gradleModule(":app")!!)
      val libraries = moduleSystem.getAndroidLibraryDependencies(DependencyScopeType.MAIN)

      val appcompat = libraries
        .first { library -> library.address.startsWith("com.android.support:support-compat") }

      assertThat(appcompat.address).matches("com.android.support:support-compat:[\\.\\d]+@aar")
      assertThat(appcompat.manifestFile?.fileName).isEqualTo(SdkConstants.FN_ANDROID_MANIFEST_XML)
      assertThat(appcompat.resFolder!!.root.toFile()).isDirectory()
    }
  }

  @Test
  fun testGetDefaultApkFile() {
    // TODO(b/191146142): Remove assumption when fixed.
    assume().that(testDefinition!!.agpVersion).isNotEqualTo(AgpVersionSoftwareEnvironmentDescriptor.AGP_40)
    // TODO(b/191146142): Remove assumption when fixed.
    assume().that(testDefinition!!.agpVersion).isNotEqualTo(AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    runTestOn(TestProject.SIMPLE_APPLICATION) { project ->
      // Invoke assemble task to generate output listing file and apk file.
      project.buildAndWait { invoker -> invoker.assemble(arrayOf(project.gradleModule(":app")!!)) }
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
    fun Project.libModule() = this.gradleModule(":lib")!!
    fun Project.appModuleSystem() = this.appModule().getModuleSystem()
    fun Project.libModuleSystem() = this.libModule().getModuleSystem()

    runTestOn(AndroidCoreTestProject.APPLICATION_ID_SUFFIX) { project ->
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      val agpVersion = testDefinition!!.agpVersion
      expect.that(project.appModuleSystem().getTestPackageName())
        .isEqualTo(if (agpVersion >= AgpVersionSoftwareEnvironmentDescriptor.AGP_80 || agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_41 || agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_42) "one.name.test" else "one.name.test_app")
      expect.that(project.libModuleSystem().getPackageName()).isEqualTo("one.name.lib")
      expect.that(project.libModuleSystem().getTestPackageName()).isEqualTo("one.name.lib.test")

      switchVariant(project, ":app", "release")
      expect.that(project.appModuleSystem().getPackageName()).isEqualTo("one.name")
      expect.that(project.appModuleSystem().getTestPackageName()).isNull()
      expect.that(project.libModuleSystem().getPackageName()).isEqualTo("one.name.lib")
      expect.that(project.libModuleSystem().getTestPackageName()).isNull()
    }
  }

  private fun runTestOn(testProject: TemplateBasedTestProject, test: (Project) -> Unit) {
    val testDefinition = testDefinition!!
    projectRule.outputCurrentlyRunningTest(testDefinition)
    if (!testDefinition.modelsV2) {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    }
    try {
      val preparedProject = projectRule.prepareTestProject(testProject, agpVersion = testDefinition.agpVersion)
      preparedProject.open(body = test)
    }
    finally {
      if (!testDefinition.modelsV2) {
        StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
      }
    }
  }
}