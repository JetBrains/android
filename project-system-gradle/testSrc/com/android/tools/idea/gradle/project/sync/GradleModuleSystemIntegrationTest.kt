/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.manifmerger.ManifestSystemProperty
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.project.sync.snapshots.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceInContent
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.DependencyScopeType.ANDROID_TEST
import com.android.tools.idea.projectsystem.DependencyScopeType.MAIN
import com.android.tools.idea.projectsystem.DependencyScopeType.UNIT_TEST
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
abstract class GradleModuleSystemIntegrationTest : GradleIntegrationTest {

  @RunWith(Parameterized::class)
  class CurrentAgp : GradleModuleSystemIntegrationTest() {

    companion object {
      @Suppress("unused")
      @Contract(pure = true)
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun test(): Collection<*> {
        return tests.map { listOf(it).toTypedArray() }
      }
    }
  }

  companion object {
    val tests =
      listOf(TestDefinition(agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT))
  }

  data class TestDefinition(
    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  ) : AgpIntegrationTestDefinition {
    override val name: String = ""
    override fun toString(): String = displayName()
    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDefinition = copy(agpVersion = agpVersion)
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  override fun getAgpVersionSoftwareEnvironmentDescriptor(): AgpVersionSoftwareEnvironmentDescriptor {
    return testDefinition?.agpVersion ?: AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }

  @Test
  fun manifestOverrides() {
    prepareGradleProject(TestProjectToSnapshotPaths.MULTI_FLAVOR, "project")
    openPreparedProject("project") { project ->

      run {
        val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
        expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
        expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
        expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
        expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
        expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.firstAbc.secondAbc.debug")
        expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION]).isEqualTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
        expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("20")
        expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.secondAbc-firstAbc-secondAbc-debug")
        expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
        expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
        expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
        expect.that(ManifestSystemProperty.values.size).isEqualTo(14)
      }
      run {
        switchVariant(project, ":app", "firstXyzSecondXyzRelease")
        val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
        expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
        expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
        expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
        expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
        expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.secondXyz.release")
        expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
        expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION]).isEqualTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
        expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("31")
        expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.0-secondXyz-release")
        expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
        expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
        expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
        expect.that(ManifestSystemProperty.values.size).isEqualTo(14)
      }
    }
  }

  @Test
  fun manifestOverridesInLibrary() {
    prepareGradleProject(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB, "withLib")
    openPreparedProject("withLib") { project ->
      val overrides = project.gradleModule(":lib")!!.getModuleSystem().getManifestOverrides().directOverrides
      assertThat(overrides).containsExactlyEntriesIn(mapOf(
        ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
        ManifestSystemProperty.Document.PACKAGE to "com.example.lib",
        ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
        ManifestSystemProperty.Manifest.VERSION_CODE to "1",
        ManifestSystemProperty.Manifest.VERSION_NAME to "1.0",
      ))
    }
  }

  @Test
  fun manifestOverridesInSeparateTest() {
    val agpVersion = getAgpVersionSoftwareEnvironmentDescriptor()
    val preparedProject = prepareGradleProject(TestProjectToSnapshotPaths.TEST_ONLY_MODULE, "withTestOnly", agpVersion)
    // This was added as a workaround to cherry-pick b/256669321 with the minimum required changes
    // been this part already refactored using the projectRule.prepareTestProject with TestProject
    if (agpVersion < AgpVersionSoftwareEnvironmentDescriptor.AGP_42) {
      preparedProject.resolve("settings.gradle").replaceInContent(", ':benchmark'", "")
    }
    openPreparedProject("withTestOnly") { project ->
      val overrides = project.gradleModule(":test")!!.getModuleSystem().getManifestOverrides().directOverrides
      assertThat(overrides).containsExactlyEntriesIn(mapOf(
        ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
        ManifestSystemProperty.Document.PACKAGE to "com.example.android.app.testmodule",
        ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
      ))
    }
  }

  @Test
  fun packageName() {
    prepareGradleProject(TestProjectToSnapshotPaths.MULTI_FLAVOR, "project")
    openPreparedProject("project") { project ->

      run {
        val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
        expect.that(packageName).isEqualTo("com.example.multiflavor")
      }
      run {
        switchVariant(project, ":app", "firstXyzSecondXyzRelease")
        val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
        expect.that(packageName).isEqualTo("com.example.multiflavor")
      }
    }
  }

  @Test
  fun allApplicationIds() {
    prepareGradleProject(TestProjectToSnapshotPaths.MULTI_FLAVOR, "project")
    openPreparedProject("project") { project ->
      val appIds = AndroidModel.get(project.gradleModule(":app")!!)?.allApplicationIds.orEmpty()
      expect.that(appIds).isEqualTo(
        setOf(
          "com.example.multiflavor.firstAbc.secondAbc.debug",
          "com.example.multiflavor.firstAbc.secondXyz.debug",
          "com.example.multiflavor.secondAbc.debug",
          "com.example.multiflavor.secondXyz.debug",
          "com.example.multiflavor.firstAbc.secondAbc.release",
          "com.example.multiflavor.firstAbc.secondXyz.release",
          "com.example.multiflavor.secondAbc.release",
          "com.example.multiflavor.secondXyz.release"
        )
      )
    }
  }

  private val String.gradleCoordinate get() =
    GradleCoordinate.parseCoordinateString(this) ?: error("Invalid gradle coordinate: $this")

  private val String.gradleVersion get() = GradleVersion.parse(this)

  @Test
  fun getResolvedDependency() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      val module = project.gradleModule(":app")?.getModuleSystem() ?: error(":app module not found")
      expect
        .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, MAIN)?.version)
        .isEqualTo("19.0".gradleVersion)
      expect
        .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, MAIN)?.version)
        .isNull()
      expect
        .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, MAIN)?.version)
        .isNull()

      expect
        .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, UNIT_TEST)?.version)
        .isEqualTo("19.0".gradleVersion)
      expect
        .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, UNIT_TEST)?.version)
        .isEqualTo("4.12".gradleVersion)
      expect
        .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, UNIT_TEST)?.version)
        .isNull()

      expect
        .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, ANDROID_TEST)?.version)
        .isEqualTo("19.0".gradleVersion)
      expect
        .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, ANDROID_TEST)?.version)
        .isEqualTo("4.12".gradleVersion)
      expect
        .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, ANDROID_TEST)?.version)
        .isEqualTo("3.0.2".gradleVersion)
    }
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}
