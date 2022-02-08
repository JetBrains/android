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

import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

@RunsInEdt
class PlatformIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testModelBuildServiceInCompositeBuilds() {
    val compositeBuildRoot = prepareGradleProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, "project")
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
    openPreparedProject("project") { project ->
      for (module in ModuleManager.getInstance(project).modules) {
        if (ExternalSystemApiUtil.getExternalModuleType(module) == "sourceSet") continue

        val gradleTestModel: TestGradleModel? = CapturePlatformModelsProjectResolverExtension.getTestGradleModel(module)
        expect.that(gradleTestModel).named("TestGradleModel($module)").isNotNull()

        val gradleParameterizedTestModel: TestParameterizedGradleModel? =
          CapturePlatformModelsProjectResolverExtension.getTestParameterizedGradleModel(module)
        // TODO(b/202448739): Remove `if` when support for parameterized models in included builds is fixed in the IntelliJ platform.
        if (module.getGradleProjectPath()?.buildRoot == toSystemIndependentName(compositeBuildRoot.absolutePath)) {
          expect.that(gradleParameterizedTestModel).named("TestParameterizedGradleModel($module)").isNotNull()
          if (gradleParameterizedTestModel != null) {
            expect.that(gradleParameterizedTestModel.message).named("TestParameterizedGradleModel($module).message")
              .isEqualTo("Parameter: EHLO BuildDir: ${ExternalSystemApiUtil.getExternalProjectPath(module)}/build")
          }
        }
      }
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> = emptyList()
}
