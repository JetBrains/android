/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.android.tools.idea.testing.updateTestProjectFromAndroidModel
import com.google.common.truth.Truth
import com.intellij.testFramework.PlatformTestCase
import java.io.File

/**
 * Tests for [ProjectStructure].
 */
class ProjectStructureTest : PlatformTestCase() {

  fun testAppModulesAndAgpVersionsAreRecorded() { // Set up modules in the project: 1 Android app, 1 Instant App, 1 Android library and 1 Java library.
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder(":"),
      androidModule(":app", "3.0.0", IdeAndroidProjectType.PROJECT_TYPE_APP),
      androidModule(":instantApp", "3.1.0", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP),
      androidModule(":androidLib", "2.3.1", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY),
      javaModule(":javaLib")
    )
    val projectStructure = ProjectStructure.getInstance(project)
    // Verify that the app modules where properly identified.
    val appModules = projectStructure.appHolderModules.map { it.name }
    Truth.assertThat(appModules)
      .containsAllOf("testAppModulesAndAgpVersionsAreRecorded.app", "testAppModulesAndAgpVersionsAreRecorded.instantApp")
    val agpPluginVersions = projectStructure.androidPluginVersions
    // Verify that the AGP versions were recorded correctly.
    val allVersions = agpPluginVersions.allVersions
    Truth.assertThat(allVersions).containsExactly(AgpVersion.parse("3.0.0"), AgpVersion.parse("3.1.0"), AgpVersion.parse("2.3.1"))
  }

  fun testAndroidPluginVersionChanged() {
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder(":"),
      androidModule(":app", "3.0.0", IdeAndroidProjectType.PROJECT_TYPE_APP),
    )

    // Verify that the AGP versions were recorded correctly.
    Truth.assertThat(ProjectStructure.getInstance(project).androidPluginVersions.allVersions).containsExactly(AgpVersion.parse("3.0.0"))

    updateTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder(":"),
      androidModule(":app", "7.0.0", IdeAndroidProjectType.PROJECT_TYPE_APP),
    )

    // Verify that the AGP versions were updated correctly.
    Truth.assertThat(ProjectStructure.getInstance(project).androidPluginVersions.allVersions).containsExactly(AgpVersion.parse("7.0.0"))
  }

  private fun javaModule(gradlePath: String, buildable: Boolean = true) = JavaModuleModelBuilder(gradlePath, buildable)

  private fun androidModule(
    gradlePath: String,
    agpVersion: String,
    projectType: IdeAndroidProjectType,
    dynamicFeatures: List<String> = emptyList(),
    moduleDependencies: List<String> = emptyList()
  ) =
    AndroidModuleModelBuilder(
      gradlePath,
      agpVersion = agpVersion,
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        projectType = { projectType },
        dynamicFeatures = { dynamicFeatures },
        androidModuleDependencyList = { moduleDependencies.map { AndroidModuleDependency(it, "debug") } }
      )
    )
}
