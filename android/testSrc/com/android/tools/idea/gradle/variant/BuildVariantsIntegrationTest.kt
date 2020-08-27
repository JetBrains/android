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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

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
      switchVariant(project, ":app", "release")
      expect.thatModuleVariantIs(project, ":app", "release")
    }
  }

  @Test
  fun testSwitchVariantsWithDependentModules() {
    prepareGradleProject(TestProjectPaths.DEPENDENT_MODULES, "project")
    openPreparedProject("project") { project ->
      switchVariant(project, ":app", "basicRelease")
      expect.thatModuleVariantIs(project, ":app", "basicRelease")
      expect.thatModuleVariantIs(project, ":lib", "release")
    }
  }

  @Test
  fun testSwitchVariantsWithDependentModules_fromLib() {
    prepareGradleProject(TestProjectPaths.DEPENDENT_MODULES, "project")
    openPreparedProject("project") { project ->
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
      expect.thatModuleVariantIs(project, ":app", /* TODO(b/166240410): "release"*/ "debug")
      expect.thatModuleVariantIs(project, "TestCompositeLib1:lib", /* TODO(b/166240410): "release"*/ "debug")
    }
  }

  private fun switchVariant(project: Project, moduleGradlePath: String, variant: String) {
    BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, project.gradleModule(moduleGradlePath)!!.name, variant)
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

