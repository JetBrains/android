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

import com.android.AndroidProjectTypes
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.testing.Facets
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.PlatformTestCase
import junit.framework.TestCase
import org.mockito.Mockito

/**
 * Tests for [ProjectStructure].
 */
class ProjectStructureTest : PlatformTestCase() {
  private lateinit var projectStructure: ProjectStructure

  override fun setUp() {
    super.setUp()
    projectStructure = ProjectStructure(project)
  }

  fun testAppModulesAndAgpVersionsAreRecorded() { // Set up modules in the project: 1 Android app, 1 Instant App, 1 Android library and 1 Java library.
    val appModule = createAndroidModule("app", "3.0", AndroidProjectTypes.PROJECT_TYPE_APP)
    val instantAppModule = createAndroidModule("instantApp", "3.1", AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP)
    createAndroidModule("androidLib", "2.3.1", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
    createJavaModule("javaLib", true /* buildable */)
    // Method to test:
    projectStructure.analyzeProjectStructure()
    // Verify that the app modules where properly identified.
    val appModules = projectStructure.appModules
    Truth.assertThat(appModules).containsAllOf(appModule, instantAppModule)
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
    val appModule = createAndroidModule("app", "3.0", AndroidProjectTypes.PROJECT_TYPE_APP)
    val instantAppModule = createAndroidModule("instantApp", "3.0", AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP)
    val androidLib = createAndroidModule("androidLib", "3.0", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
    // Make appModule depend on androidLib
    ApplicationManager.getApplication().runWriteAction(ThrowableComputable<Void?, Throwable?> {
      val rootManager = ModuleRootManager.getInstance(appModule)
      val modifiableModel = rootManager.modifiableModel
      modifiableModel.addModuleOrderEntry(androidLib)
      try {
        modifiableModel.commit()
      }
      catch (e: Throwable) {
        modifiableModel.dispose()
        throw e
      }
      null
    })
    val leaf1 = createAndroidModule("leaf1", "3.0", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
    val leaf2 = createJavaModule("leaf2", true /* buildable */)
    // This module should not be considered a "leaf" since it is not buildable.
    val leaf3 = createJavaModule("leaf3", false /* not buildable */)
    // Method to test:
    projectStructure.analyzeProjectStructure()
    // Verify that app and leaf modules are returned.
    val leafModules = projectStructure.leafModules
    Truth.assertThat(leafModules).containsExactly(appModule, instantAppModule, leaf1, leaf2)
    Truth.assertThat(leafModules).doesNotContain(leaf3)
  }

  fun testLeafModulesContainsBaseAndFeatureModules() {
    val appModule = createAndroidModule("app", "3.2", AndroidProjectTypes.PROJECT_TYPE_APP)
    val feature1Module = createAndroidModule("feature1", "3.2", AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE)
    val feature2Module = createAndroidModule("feature2", "3.2", AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE)
    // Make appModule depend on feature1Module and feature2Module
    ApplicationManager.getApplication().runWriteAction(ThrowableComputable<Void?, Throwable?> {
      val rootManager = ModuleRootManager.getInstance(appModule)
      val modifiableModel = rootManager.modifiableModel
      modifiableModel.addModuleOrderEntry(feature1Module)
      modifiableModel.addModuleOrderEntry(feature2Module)
      try {
        modifiableModel.commit()
      }
      catch (e: Throwable) {
        modifiableModel.dispose()
        throw e
      }
      null
    })
    // Method to test:
    projectStructure.analyzeProjectStructure()
    // Verify that the app modules where properly identified.
    val appModules = projectStructure.appModules
    Truth.assertThat(appModules).containsExactly(appModule)
    // Verify that app and leaf modules are returned.
    val leafModules = projectStructure.leafModules
    Truth.assertThat(leafModules).containsExactly(appModule, feature1Module, feature2Module)
  }

  private fun createAndroidModule(name: String, pluginVersion: String, projectType: Int): Module {
    val module = createGradleModule(name)
    setUpAsAndroidModule(module, pluginVersion, projectType)
    return module
  }

  private fun createJavaModule(name: String, buildable: Boolean): Module {
    val module = createGradleModule(name)
    setUpAsJavaModule(module, buildable)
    return module
  }

  private fun createGradleModule(name: String): Module {
    val module = createModule(name)
    val gradleFacet = Facets.createAndAddGradleFacet(module)
    gradleFacet.configuration.GRADLE_PROJECT_PATH = ":$name"
    return module
  }

  companion object {
    private fun setUpAsAndroidModule(module: Module, pluginVersion: String, projectType: Int) {
      val androidFacet = Facets.createAndAddAndroidFacet(module)
      val androidModel = Mockito.mock(AndroidModuleModel::class.java)
      AndroidModel.set(androidFacet, androidModel)
      androidFacet.configuration.projectType = projectType
      val androidProject = Mockito.mock(IdeAndroidProject::class.java)
      Mockito.`when`(androidProject.projectType).thenReturn(projectType)
      Mockito.`when`(androidModel.androidProject).thenReturn(androidProject)
      Mockito.`when`(androidModel.modelVersion).thenReturn(GradleVersion.parse(pluginVersion))
    }

    private fun setUpAsJavaModule(module: Module, buildable: Boolean) {
      val javaFacet = Facets.createAndAddJavaFacet(module)
      javaFacet.configuration.BUILDABLE = buildable
    }
  }
}