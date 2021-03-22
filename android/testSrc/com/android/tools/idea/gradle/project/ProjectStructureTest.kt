/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.gradle.model.IdeAndroidProjectType
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.truth.Truth
import com.intellij.testFramework.PlatformTestCase
import junit.framework.TestCase
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
      androidModule(":app", "3.0", IdeAndroidProjectType.PROJECT_TYPE_APP),
      androidModule(":instantApp", "3.1", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP),
      androidModule(":androidLib", "2.3.1", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY),
      javaModule(":javaLib")
    )
    val projectStructure = ProjectStructure.getInstance(project)
    // Verify that the app modules where properly identified.
    val appModules = projectStructure.appModules.map { it.name }
    Truth.assertThat(appModules).containsAllOf("app", "instantApp")
    val agpPluginVersions = projectStructure.androidPluginVersions
    TestCase.assertFalse(agpPluginVersions.isEmpty)
    // Verify that the AGP versions were recorded correctly.
    val internalMap = agpPluginVersions.internalMap
    Truth.assertThat(internalMap).containsEntry(":app", GradleVersion.parse("3.0"))
    Truth.assertThat(internalMap).containsEntry(":instantApp", GradleVersion.parse("3.1"))
    Truth.assertThat(internalMap).containsEntry(":androidLib", GradleVersion.parse("2.3.1"))
    Truth.assertThat(internalMap).doesNotContainKey(":javaLib")
  }

  fun testLeafModulesAreRecorded() {
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder.rootModuleBuilder,
      androidModule(":app", "3.0", IdeAndroidProjectType.PROJECT_TYPE_APP, moduleDependencies = listOf(":androidLib")),
      androidModule(":instantApp", "3.0", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP),
      androidModule(":androidLib", "3.0", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY),
      androidModule(":leaf1", "3.0", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY),
      javaModule(":leaf2"),
      javaModule(":leaf3", buildable = false)
    )

    val projectStructure = ProjectStructure.getInstance(project)
    // Verify that app and leaf modules are returned.
    val leafModules = projectStructure.leafModules.map { it.name }
    Truth.assertThat(leafModules).containsExactly("app", "instantApp", "leaf1", "leaf2")
    Truth.assertThat(leafModules).doesNotContain("leaf3")
  }

  fun testLeafModulesContainsBaseAndFeatureModules() {
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder.rootModuleBuilder,
      androidModule(":app", "3.2", IdeAndroidProjectType.PROJECT_TYPE_APP, dynamicFeatures = listOf(":feature1", ":feature2")),
      androidModule(":feature1", "3.2", IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE, moduleDependencies = listOf(":app")),
      androidModule(":feature2", "3.2", IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE, moduleDependencies = listOf(":app"))
    )

    val projectStructure = ProjectStructure.getInstance(project)
    // Verify that the app modules where properly identified.
    val appModules = projectStructure.appModules.map { it.name }
    Truth.assertThat(appModules).containsExactly("app")
    // Verify that app and leaf modules are returned.
    val leafModules = projectStructure.leafModules.map { it.name }
    Truth.assertThat(leafModules).containsExactly("app", "feature1", "feature2")
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
