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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_COMPOSITE_SINGLE
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CompositeProjectCatalogBuildModelTest : GradleFileModelTestCase() {
  private lateinit var compositeRoot: VirtualFile
  private lateinit var gradle: VirtualFile

  @Before
  fun prepare() {
    writeToBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD)
    writeToNewProjectFile("applied", COMPOSITE_BUILD_MAIN_PROJECT_APPLIED)
    writeToSubModuleBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD)
    writeToSettingsFile(COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS)
    writeToVersionCatalogFile("""
      [libraries]
      guava = "com.google.guava:guava:19.0"
    """.trimIndent())

    // Set up the composite project.
    runWriteAction<Unit, IOException> {
      compositeRoot = myProjectBasePath.createChildDirectory(this, "CompositeBuild")
      assertTrue(compositeRoot.exists())
      // no settings file
      createFileAndWriteContent(compositeRoot.createChildData(this, "build$myTestDataExtension"), COMPOSITE_BUILD_COMPOSITE_SINGLE)
      gradle = compositeRoot.createChildDirectory(this, "gradle")
      assertTrue(gradle.exists())
      saveFileUnderWrite(gradle.createChildData(this, "libs.versions.toml"), """
        [libraries]
        new_guava = "com.google.guava:guava:20.0"
      """.trimIndent())
    }
  }

  @Test
  fun testEnsureVersionCatalogIsTakenFromCompositeBuild() {
    // Create both ProjectBuildModels
    val mainModel = projectBuildModel
    val compositeModel = getIncludedProjectBuildModel(compositeRoot.path)
    assertNotNull(compositeModel)

    val libs = compositeModel!!.versionCatalogsModel.getVersionCatalogModel("libs")
    assertNotNull(libs)
    val allLibraries = libs!!.libraryDeclarations().getAll()
    assertSize(1, allLibraries.values)

    // model should have included build catalog but not composite project (parent) catalog
    assertNotNull(allLibraries.get("new_guava"))
  }


  private fun createFileAndWriteContent(file: VirtualFile, content: TestFileName) {
    assertTrue(file.exists())
    prepareAndInjectInformationForTest(content, file)
  }
}
