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
package com.android.tools.idea.gradle.variant

import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files

@RunWith(JUnit4::class)
@RunsInEdt
class BuildVariantsIntegrationTest : GradleIntegrationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @get:Rule
  val testName = TestName()

  @Test
  fun testSwitchVariants() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")

      switchVariant(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app", "debug")
      // TODO(b/184824343): Re-enable when fixed.
      // expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariants_Kapt() {
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")

      switchVariant(project, ":app", "debug")
      expect.thatModuleVariantIs(project, ":app", "debug")
      // TODO(b/184824343): Re-enable when fixed.
      // expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun testSwitchVariants_symlinks() {
    assumeNotWindows()

    val path = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val suffix = "_sm"
    val symlink_path = File(path.path + suffix)
    Files.createSymbolicLink(symlink_path.toPath(), path.toPath())
    openPreparedProject("project$suffix") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")
      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
    }
  }

  @Test
  fun testSwitchVariantsWithDependentModules() {
    prepareGradleProject(TestProjectPaths.DEPENDENT_MODULES, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      expect.thatModuleVariantIs(project, ":lib", "debug")
      switchVariant(project, ":app", "basicRelease")
      expect.thatModuleVariantIs(project, ":app", "basicRelease")
      expect.thatModuleVariantIs(project, ":lib", "release")
    }
  }

  @Test
  fun testSwitchVariantsWithFeatureModules() {
    prepareGradleProject(TestProjectPaths.DYNAMIC_APP_WITH_VARIANTS, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "fl1AbDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl1AbDebug")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl1AbDimFl1Debug")
      switchVariant(project, ":app", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":app", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":feature1", "fl2AbRelease")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2AbDimFl1Release")
      switchVariant(project, ":dependsOnFeature1", "fl2XyDimFl2Debug")
      expect.thatModuleVariantIs(project, ":dependsOnFeature1", "fl2XyDimFl2Debug")
      expect.thatModuleVariantIs(project, ":app", "fl2XyDebug")
      expect.thatModuleVariantIs(project, ":feature1", "fl2XyDebug")
    }
  }

  @Test
  fun testSwitchVariantsWithDependentModules_fromLib() {
    prepareGradleProject(TestProjectPaths.DEPENDENT_MODULES, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      expect.thatModuleVariantIs(project, ":lib", "debug")
      switchVariant(project, ":lib", "release")
      expect.thatModuleVariantIs(project, ":app", "basicDebug")
      // TODO(b/166244122): Consider propagating variant selection in both directions (even though it might not yield the correct results).
      expect.thatModuleVariantIs(project, ":lib", "release")
    }
  }

  @Test
  fun testSwitchVariantsInCompositeBuildProject() {
    prepareGradleProject(TestProjectPaths.COMPOSITE_BUILD, "project")
    openPreparedProject("project") { project ->
      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.thatModuleVariantIs(project, "TestCompositeLib1:lib", "release")
    }
  }

  @Test
  fun `sync after switching variants`() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")

      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      val releaseSnapshot = project.saveAndDump()

      project.requestSyncAndWait()
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)
    }
  }

  @Test
  fun `switch reopen and switch back`() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val (debugSnapshot, releaseSnapshot) = openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      debugSnapshot to project.saveAndDump()
    }
    openPreparedProject("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.thatModuleVariantIs(project, ":app", "release")
      expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)

      switchVariant(project, ":app", "debug")
      // TODO(b/184824343): Re-enable when fixed.
      // expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  @Test
  fun `switch reopen and switch back with Kotlin and Kapt`() {
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    val (debugSnapshot, releaseSnapshot) = openPreparedProject("project") { project ->
      expect.thatModuleVariantIs(project, ":app", "debug")
      val debugSnapshot = project.saveAndDump()

      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
      debugSnapshot to project.saveAndDump()
    }
    openPreparedProject("project") { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.thatModuleVariantIs(project, ":app", "release")
      // TODO(b/184824343): expect.that(project.saveAndDump()).isEqualTo(releaseSnapshot)

      switchVariant(project, ":app", "debug")
      // TODO(b/184824343): expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      expect.thatModuleVariantIs(project, ":app", "debug")
      expect.that(project.saveAndDump()).isEqualTo(debugSnapshot)
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}

private fun Module.selectedVariant(): String? = AndroidModuleModel.get(this)?.selectedVariant?.name
private fun Project.moduleVariant(gradlePath: String) = gradleModule(gradlePath)?.selectedVariant()
private fun Expect.thatModuleVariantIs(project: Project, gradlePath: String, variant: String) =
    withMessage("Selected variant in module $gradlePath").that(project.moduleVariant(gradlePath)).isEqualTo(variant)

