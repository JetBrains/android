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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.model.BuildModelContext.ResolvedConfigurationFileLocationProvider
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelTest.TestFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.SystemIndependent
import org.junit.Test

class CustomContextProjectBuildModelTest : GradleFileModelTestCase() {

  override fun createContext(): BuildModelContext {
    return BuildModelContext.create(project, object : ResolvedConfigurationFileLocationProvider {
      override fun getGradleBuildFile(module: Module): VirtualFile? = null
      override fun getGradleProjectRootPath(module: Module): @SystemIndependent String? = null
      override fun getGradleProjectRootPath(project: Project): @SystemIndependent String =
        throw RuntimeException("Method should not be called")
    })
  }

  // Regression for b/328089063
  @Test
  fun testVersionCatalogMultipleRoots() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile("")
    val pbm = projectBuildModel
    val vcModel = pbm.versionCatalogsModel
    assertContainsElements(vcModel.catalogNames(), "libs")
  }

}