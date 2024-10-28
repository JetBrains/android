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
package com.android.tools.idea.npw.module

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.multiplatform.NewKotlinMultiplatformLibraryModuleModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class AddKotlinMultiplatformLibraryModuleTest {

  companion object {

    private fun addNewKotlinMultiplatformLibraryModule(projectRule: AndroidGradleProjectRule) {
      projectRule.load(TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM)

      val project = projectRule.project
      val model =
        NewKotlinMultiplatformLibraryModuleModel(
            project = project,
            moduleParent = ":",
            projectSyncInvoker = emptyProjectSyncInvoker,
          )
          .apply {
            packageName.set("com.example.shared")
            agpVersion.set(AgpVersion(8, 1, 0))
          }

      model.handleFinished() // Generate module files

      projectRule.invokeTasks("compileAndroidMain").run {
        buildError?.printStackTrace()
        Assert.assertTrue("androidMain didn't compile correctly", isBuildSuccessful)
      }

      projectRule.invokeTasks("testAndroidTestOnJvm").run {
        buildError?.printStackTrace()
        Assert.assertTrue("androidTestOnJvm didn't compile or run correctly", isBuildSuccessful)
      }

      projectRule.invokeTasks("packageAndroidTestOnDevice").run {
        buildError?.printStackTrace()
        Assert.assertTrue("androidTestOnDevice didn't package correctly", isBuildSuccessful)
      }
    }

    // Ignore project sync (to speed up test), if later we are going to perform a gradle build
    // anyway.
    private val emptyProjectSyncInvoker =
      object : ProjectSyncInvoker {
        override fun syncProject(project: Project) {}
      }
  }

  @get:Rule val projectRule = AndroidGradleProjectRule()

  @Test
  fun addNewKotlinMultiplatformLibraryModuleTest() {
    addNewKotlinMultiplatformLibraryModule(projectRule)
  }
}
