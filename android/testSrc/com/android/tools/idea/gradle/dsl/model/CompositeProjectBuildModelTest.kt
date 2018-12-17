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
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_COMPOSITE_PROJECT_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_COMPOSITE_PROJECT_ROOT_BUILD
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_COMPOSITE_PROJECT_SETTINGS
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_COMPOSITE_PROJECT_SUB_MODULE_BUILD
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_MAIN_PROJECT_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS
import com.android.tools.idea.gradle.dsl.TestFileName.COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import org.junit.Before
import org.junit.Test
import java.io.File

class CompositeProjectBuildModelTest : GradleFileModelTestCase() {
  lateinit var compositeRoot : File
  lateinit var compositeSub : File

  @Before
  override fun setUp() {
    super.setUp()
    writeToBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_ROOT_BUILD)
    writeToNewProjectFile("applied.gradle", COMPOSITE_BUILD_MAIN_PROJECT_APPLIED)
    writeToSubModuleBuildFile(COMPOSITE_BUILD_MAIN_PROJECT_SUB_MODULE_BUILD)
    writeToSettingsFile(COMPOSITE_BUILD_MAIN_PROJECT_SETTINGS)

    // Set up the composite project.
    compositeRoot = File(myProjectBasePath, "CompositeBuild/")
    assertTrue(compositeRoot.mkdirs())
    createFileAndWriteContent(File(compositeRoot, "settings.gradle"), COMPOSITE_BUILD_COMPOSITE_PROJECT_SETTINGS)
    createFileAndWriteContent(File(compositeRoot, "build.gradle"), COMPOSITE_BUILD_COMPOSITE_PROJECT_ROOT_BUILD)
    createFileAndWriteContent(File(compositeRoot, "subApplied.gradle"), COMPOSITE_BUILD_COMPOSITE_PROJECT_APPLIED)
    compositeSub = File(compositeRoot, "app")
    assertTrue(compositeSub.mkdirs())
    createFileAndWriteContent(File(compositeSub, "build.gradle"), COMPOSITE_BUILD_COMPOSITE_PROJECT_SUB_MODULE_BUILD)
  }

  @Test
  fun testEnsureCompositeBuildProjectDoNotLeakProperties() {
    // Create both ProjectBuildModels
    val mainModel = ProjectBuildModel.get(myProject)
    val compositeModel = ProjectBuildModel.getForCompositeBuild(myProject, compositeRoot.absolutePath)
    assertNotNull(compositeModel)

    // Check both models contain properties from the applied file
    fun checkBuildscriptDeps(model: ProjectBuildModel) {
      val buildDeps = model.projectBuildModel!!.buildscript().dependencies().artifacts()
      assertSize(2, buildDeps)
      assertEquals("com.android.tools.build:gradle:3.4.0-dev", buildDeps[0].completeModel().forceString())
      assertEquals("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.40", buildDeps[1].completeModel().forceString())
    }
    checkBuildscriptDeps(mainModel)
    checkBuildscriptDeps(compositeModel!!)

    // Check that the ext properties are only visible from the correct build models
    val mainProp = mainModel.projectBuildModel!!.ext().findProperty("mainProjectProperty")
    verifyPropertyModel(mainProp, "ext.mainProjectProperty", "false")
    val compositeProp = compositeModel.projectBuildModel!!.ext().findProperty("compositeProjectProperty")
    verifyPropertyModel(compositeProp, "ext.compositeProjectProperty", "true", File(compositeRoot, "build.gradle").absolutePath)
    val wrongMainProp = mainModel.projectBuildModel!!.ext().findProperty("compositeProjectProperty")
    assertMissingProperty(wrongMainProp)
    val wrongCompositeProp = compositeModel.projectBuildModel!!.ext().findProperty("mainProjectProperty")
    assertMissingProperty(wrongCompositeProp)

    // Check applied property in composite subModule
    val appName = compositeModel.getModuleBuildModel(compositeSub)!!.android().defaultConfig().applicationId()
    verifyPropertyModel(appName, "android.defaultConfig.applicationId", "Super cool app", File(compositeSub, "build.gradle").absolutePath)

    // Check included builds are correct in the main modules settings file
    val includedBuilds = mainModel.projectSettingsModel!!.includedBuilds()
    assertSize(1, includedBuilds)
    assertEquals(compositeRoot.absolutePath, includedBuilds[0].path)
  }

  @Test
  fun testEnsureProjectBuildModelsProduceAllBuildModels() {
    val mainModel = ProjectBuildModel.get(myProject)
    val compositeModel = ProjectBuildModel.getForCompositeBuild(myProject, compositeRoot.absolutePath)

    val mainBuildModels = mainModel.allIncludedBuildModels
    assertSize(2, mainBuildModels)

    val compositeBuildModels = compositeModel!!.allIncludedBuildModels
    assertSize(2, compositeBuildModels)
  }

  private fun createFileAndWriteContent(file: File, content: TestFileName) {
    assertTrue(file.parentFile.exists() || file.mkdirs())
    assertTrue(file.exists() || file.createNewFile())

    prepareAndInjectInformationForTest(content, file)
  }
}