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
package com.android.tools.idea.gradle.variant.runsGradleVariant

import com.android.SdkConstants
import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver
import com.android.tools.idea.gradle.project.sync.idea.getSelectedVariantAndAbis
import com.android.tools.idea.gradle.project.sync.idea.getSelectedVariants
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.switchAbi
import com.android.tools.idea.testing.switchVariant
import com.google.common.base.Charsets
import com.google.common.io.Files.asCharSource
import com.google.common.truth.Expect
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files

private const val NOT_SET = "n/a"

@RunWith(JUnit4::class)
@RunsInEdt
class BuildVariantsIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @Test
  fun testSwitchVariants() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")

      switchVariant(project, ":app", "debug")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariants_Kapt() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.KOTLIN_KAPT)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")

      switchVariant(project, ":app", "debug")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariants_symlinks() {
    assumeNotWindows()

    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val path = preparedProject.root
    val suffix = "_sm"
    val symlinkPath = File(path.path + suffix)
    Files.createSymbolicLink(symlinkPath.toPath(), path.toPath())

    projectRule.openPreparedProject("project$suffix") { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
    }
  }

  @Test
  fun testSwitchVariants_app_symlinks() {
    assumeNotWindows()

    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val path = preparedProject.root
    val app = path.resolve("app").toPath()
    val linkSourcePath = path.resolve("app_sm_src").toPath()
    Files.move(app, linkSourcePath)
    Files.createSymbolicLink(app, linkSourcePath)
    VfsUtil.markDirtyAndRefresh(false, true, true, path)

    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
    }
  }

  @Test
  fun testSwitchVariantsWithDependentModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DEPENDENT_MODULES)
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      expect.thatModuleVariantIs(project, ":lib", "debug")
      val basicDebugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "basicRelease")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "basicRelease")
      expect.thatModuleVariantIs(project, ":lib", "release")

      switchVariant(project, ":app", "basicDebug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      expect.thatModuleVariantIs(project, ":lib", "debug")
      expect.that(project.saveAndDump()).isEqualTo(basicDebugSnapshot)
    }
  }

  @Test
  fun testSwitchVariantsWithDependentNativeModules() {
    assumeNotWindows()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DEPENDENT_NATIVE_MODULES)
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "x86")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "release", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "release", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "release", abi = "x86")

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "x86")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariantsWithAbiFilters() {
    assumeNotWindows()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DEPENDENT_NATIVE_MODULES)
    val path = preparedProject.root
    val buildFile = path.resolve("app").resolve("build.gradle")
    buildFile.writeText(
      buildFile
        .readText()
        .replace(
          "buildTypes {",
          """
            flavorDimensions "dim1"
            productFlavors {
                aa {
                    dimension "dim1"
                    ndk {
                        abiFilters 'armeabi-v7a'
                    }
                }
                xx {
                    dimension "dim1"
                    ndk {
                        abiFilters 'x86'
                    }
                }
            }
            buildTypes {
          """
        )
    )
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "aaDebug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "armeabi-v7a")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "xxRelease")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "xxRelease", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "release", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "release", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "release", abi = "x86")

      switchVariant(project, ":app", "aaDebug")
// TODO(b/229736426): Uncomment when switching variants from cache with different ABIs is supported.
      //  expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      //  expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "aaDebug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "armeabi-v7a")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchAbiWithDependentNativeModules() {
    assumeNotWindows()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DEPENDENT_NATIVE_MODULES)
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "x86")
      val x86Snapshot = project.saveAndDump()

      switchAbi(project, ":app", "armeabi-v7a")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "armeabi-v7a")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "armeabi-v7a")

      switchAbi(project, ":app", "x86")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib1", "debug", abi = NOT_SET)
      expect.thatModuleVariantIs(project, ":lib2", "debug", abi = "x86")
      expect.thatModuleVariantIs(project, ":lib3", "debug", abi = "x86")
      expect.that(project.saveAndDump()).isEqualTo(x86Snapshot)
    }
  }

  @Test
  fun testSwitchVariantsWithFeatureModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DYNAMIC_APP_WITH_VARIANTS)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "fl1AbDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl1AbDebug")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl1AbDebug")
      switchVariant(project, ":app", "fl2AbRelease")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":feature1", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2AbRelease")
      switchVariant(project, ":dependsOnFeature1", "fl2XyDebug")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":app", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl2XyDebug")
    }
  }

  @Test
  fun testSwitchVariantsWithFeatureModulesAndDefaults() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DYNAMIC_APP_WITH_VARIANTS_AND_DEFAULTS)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      // default flavors are set as "fl2" and "xy" in :app
      expect.thatModuleVariantIs(project, ":app", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2XyDebug")
      switchVariant(project, ":app", "fl2AbRelease")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":feature1", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2AbRelease")
      // Switching the feature should also affect app
      switchVariant(project, ":dependsOnFeature1", "fl2XyDebug")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":app", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl2XyDebug")
    }
  }


  @Test
  fun testSwitchVariantsWithDependentModules_fromLib() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.DEPENDENT_MODULES)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      expect.thatModuleVariantIs(project, ":lib", "debug")
      switchVariant(project, ":lib", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      // TODO(b/166244122): Consider propagating variant selection in both directions (even though it might not yield the correct results).
      expect.thatModuleVariantIs(project, ":lib", "release")
    }
  }

  @Test
  fun `switching follows dependencies`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.TRANSITIVE_DEPENDENCIES)
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":library1", "debug")
      expect.thatModuleVariantIs(project, ":library2", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":library1", "release")
      expect.thatModuleVariantIs(project, ":library2", "release")

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":library1", "debug")
      expect.thatModuleVariantIs(project, ":library2", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariantsInCompositeBuildProject() {
    val preparedProject = projectRule.prepareTestProject(TestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->
      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":includedLib1:lib", "release")
    }
  }

  @Test
  fun `sync after switching variants`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")

      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      val releaseSnapshot = project.saveAndDump()

      project.requestSyncAndWait()
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)
    }
  }

  @Test
  fun `switch reopen and switch back`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val (debugSnapshot, releaseSnapshot) = preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      debugSnapshot to project.saveAndDump()
    }
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun `switch switch back and reopen`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val (debugSnapshot, releaseSnapshot) = preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      val releaseSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
      debugSnapshot to releaseSnapshot
    }
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)
    }
  }

  @Test
  fun `switch reopen and switch back with Kotlin and Kapt`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.KOTLIN_KAPT)
    val (debugSnapshot, releaseSnapshot) = preparedProject.open { project ->
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      debugSnapshot to project.saveAndDump()
    }
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun `switch variant and abi with cmake`() {
    assumeNotWindows()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.HELLO_JNI)
    val (firstSnapshot, secondSnapshot) = preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "arm7Debug", abi = "armeabi-v7a")
      val firstSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "x86Debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "x86Debug", abi = "x86")
      firstSnapshot to project.saveAndDump()
    }
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "x86Debug", abi = "x86")
      expect.that(project.saveAndDump()).isEqualTo(secondSnapshot)

      switchAbi(project, ":app", "arm64-v8a")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "arm8Debug", abi = "arm64-v8a")

      switchVariant(project, ":app", "arm7Debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "arm7Debug", abi = "armeabi-v7a")
      expect.that(project.saveAndDump()).isEqualTo(firstSnapshot)

      switchAbi(project, ":app", "x86")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "x86Debug", abi = "x86")
      expect.that(project.saveAndDump()).isEqualTo(secondSnapshot)

      switchVariant(project, ":app", "enableAllAbisDebug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "enableAllAbisDebug", abi = "x86")

      switchAbi(project, ":app", "armeabi-v7a")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "enableAllAbisDebug", abi = "armeabi-v7a")

    }
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)

      switchAbi(project, ":app", "x86")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "enableAllAbisDebug", abi = "x86")
    }
  }

  @Test
  fun `switch variants in apps with shared dependency modules`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.TRANSITIVE_DEPENDENCIES)
    val path = preparedProject.root

    // Create build file for module app2, so that
    //         app  -> library2 -> library1
    //         app2 -> library2 -> library1
    val buildFilePath = File(path, FileUtil.join("app2", SdkConstants.FN_BUILD_GRADLE))
    FileUtil.writeToFile(buildFilePath, """apply plugin: 'com.android.application'
      android {
          compileSdkVersion ${BuildEnvironment.getInstance().compileSdkVersion}
          namespace "com.example"
      }
      dependencies {
          api project(':library2')
      }""")

    // Add app2 to settings file.
    val settingsFile = File(path, SdkConstants.FN_SETTINGS_GRADLE)
    val settingsText = asCharSource(settingsFile, Charsets.UTF_8).read()
    FileUtil.writeToFile(settingsFile, settingsText.trim { it <= ' ' } + ", \":app2\"")

    // Create manifest file for app2.
    val manifest = File(path, FileUtil.join("app2", "src", "main", SdkConstants.ANDROID_MANIFEST_XML))
    FileUtil.writeToFile(manifest, """<?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      </manifest>""")

    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app2", "debug")
      expect.thatModuleVariantIs(project, ":library1", "debug")
      expect.thatModuleVariantIs(project, ":library2", "debug")
      val allDebugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app2", "debug")
      expect.thatModuleVariantIs(project, ":library1", "release")
      expect.thatModuleVariantIs(project, ":library2", "release")

      switchVariant(project, ":app2", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app2", "release")
      expect.thatModuleVariantIs(project, ":library1", "release")
      expect.thatModuleVariantIs(project, ":library2", "release")

      switchVariant(project, ":app2", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app2", "debug")
      expect.thatModuleVariantIs(project, ":library1", "debug")
      expect.thatModuleVariantIs(project, ":library2", "debug")

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app2", "debug")
      expect.thatModuleVariantIs(project, ":library1", "debug")
      expect.thatModuleVariantIs(project, ":library2", "debug")
      expect.that(project.saveAndDump()).isEqualTo(allDebugSnapshot)
    }
  }

  @Test
  fun `switch variants in app with a test dependency on an android library`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.ANDROID_LIBRARY_AS_TEST_DEPENDENCY)
    preparedProject.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":testDependency", "debug")
      val allDebugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":testDependency", "release")
      val allReleaseSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "debug")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":testDependency", "debug")
      expect.that(project.saveAndDump()).isEqualTo(allDebugSnapshot)

      switchVariant(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":testDependency", "release")
      expect.that(project.saveAndDump()).isEqualTo(allReleaseSnapshot)
    }
  }

  @Test
  fun `re-import variants to default`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.TRANSITIVE_DEPENDENCIES)

    // Add app2 module
    FileUtil.writeToFile(File(preparedProject.root, FileUtil.join("app2", SdkConstants.FN_BUILD_GRADLE)), """
      apply plugin: 'com.android.application'

      android {
          compileSdkVersion ${BuildEnvironment.getInstance().compileSdkVersion}
          namespace "com.example"
      }
    """.trimIndent())
    val settingsFile = File(preparedProject.root, SdkConstants.FN_SETTINGS_GRADLE)
    val settingsText = asCharSource(settingsFile, Charsets.UTF_8).read()
    FileUtil.writeToFile(settingsFile, settingsText.trim { it <= ' ' } + ", \":app2\"")
    val manifest = File(preparedProject.root, FileUtil.join("app2", "src", "main", SdkConstants.ANDROID_MANIFEST_XML))
    FileUtil.writeToFile(manifest, """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      </manifest>
    """.trimIndent())

    preparedProject.open { project ->
      // Switch app module variant to release
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app2", "debug")
      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app2", "debug")

      // Validate all variants reset to default (debug)
      var request = GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER, importDefaultVariants = true)
      project.requestSyncAndWait(syncRequest = request)
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app2", "debug")

      // Switch both modules to release variant
      switchVariant(project, ":app", "release")
      switchVariant(project, ":app2", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app2", "release")

      // Validate all variants get set to default (debug)
      request = GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER, importDefaultVariants = true)
      project.requestSyncAndWait(syncRequest = request)
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.consistentConfigurationOf(project)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app2", "debug")
    }
  }
}

private fun Module.selectedModelVariant(): String? = GradleAndroidModel.get(this)?.selectedVariant?.name
private fun Module.selectedNdkModelVariant(): String? = NdkModuleModel.get(this)?.selectedVariant
private fun Module.selectedNdkModelAbi(): String? = NdkModuleModel.get(this)?.selectedAbi
private fun Module.selectedFacetVariant(): String? = AndroidFacet.getInstance(this)?.properties?.SELECTED_BUILD_VARIANT
private fun Module.selectedNdkFacetVariant(): String? = NdkFacet.getInstance(this)?.selectedVariantAbi?.variant
private fun Module.selectedNdkFacetAbi(): String? = NdkFacet.getInstance(this)?.selectedVariantAbi?.abi

private fun Expect.consistentConfigurationOf(project: Project) {
  val facetVariants = project.getSelectedVariantAndAbis()
  val projectStructure = ProjectDataManager.getInstance()
    .getExternalProjectData(project, GradleConstants.SYSTEM_ID, toCanonicalPath(File(project.basePath!!).canonicalPath))
    ?.externalProjectStructure
  val modelVariants = projectStructure?.getSelectedVariants()
  withMessage("Variants and ABI configured in facets").that(facetVariants).isEqualTo(modelVariants)

  val variants =
    (projectStructure?.let { find(it, AndroidGradleProjectResolver.CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS) }
      ?.data
      ?.data
      ?.map {it.getSelectedVariants()}.orEmpty() + listOfNotNull(modelVariants))

  withMessage("Stored variants are unique").that(variants).containsNoDuplicates()
}

private fun Expect.thatModuleVariantIs(project: Project, gradlePath: String, variant: String, abi: String? = null) {
  val module = project.gradleModule(gradlePath)
  withMessage("Selected variant in AndroidModel $gradlePath").that(module?.selectedModelVariant()).isEqualTo(variant)
  withMessage("Selected variant in AndroidFacet $gradlePath").that(module?.selectedFacetVariant()).isEqualTo(variant)
  if (abi != null) {
    withMessage("Selected variant in NdkModuleModel $gradlePath")
      .that(module?.selectedNdkModelVariant()).isEqualTo(variant.takeUnless { abi == NOT_SET })
    withMessage("Selected variant in NdkFacet $gradlePath")
      .that(module?.selectedNdkFacetVariant()).isEqualTo(variant.takeUnless { abi == NOT_SET })
    withMessage("Selected ABI in NdkModuleModel $gradlePath").that(module?.selectedNdkModelAbi() ?: NOT_SET).isEqualTo(abi)
    withMessage("Selected ABI in NdkFacet $gradlePath").that(module?.selectedNdkFacetAbi() ?: NOT_SET).isEqualTo(abi)
  }
}

