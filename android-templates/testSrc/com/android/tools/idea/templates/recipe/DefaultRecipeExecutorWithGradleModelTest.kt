/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe

import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.lint.common.getModuleDir
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import org.jetbrains.annotations.SystemDependent
import org.junit.Before
import org.junit.Test
import java.io.File

class DefaultRecipeExecutorWithGradleModelTest : GradleFileModelTestCase("tools/adt/idea/android-templates/testData/recipe") {

  private val mockProjectTemplateData = MockitoKt.mock<ProjectTemplateData>()
  private val mockModuleTemplateData = MockitoKt.mock<ModuleTemplateData>()
  private val mockVersionCatalogDetector = MockitoKt.mock<GradleVersionCatalogDetector>()

  private val renderingContext by lazy {
    RenderingContext(
      project,
      module,
      "DefaultRecipeExecutor test with gradle model",
      mockModuleTemplateData,
      moduleRoot = module.getModuleDir(),
      dryRun = false,
      showErrors = true
    )
  }
  private val recipeExecutor by lazy {
    DefaultRecipeExecutor(renderingContext, mockVersionCatalogDetector)
  }

  @Before
  fun init() {
    MockitoKt.whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)
    MockitoKt.whenever(mockProjectTemplateData.gradlePluginVersion).thenReturn("8.0.0")
    MockitoKt.whenever(mockVersionCatalogDetector.versionCatalogDetectorResult).thenReturn(
      GradleVersionCatalogDetector.DetectorResult.IMPLICIT_LIBS_VERSIONS)
  }

  @Test
  fun testAddDependencyWithVersionCatalog() {
    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_alreadyExists() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """)
    writeToBuildFile(TestFile.VERSION_CATALOG_ADD_DEPENDENCY)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify library is not duplicated in the toml file and the build file
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_alreadyExists_asModuleRepresentation() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """)
    writeToBuildFile(TestFile.VERSION_CATALOG_ADD_DEPENDENCY)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify library is not duplicated in the toml file and the build file
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_sameNameExist() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
    """)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify avoiding the same name with different module
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_sameNameExist_includingGroupName() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
    """)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify avoiding the same name with different module
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx231 = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx231 = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx231" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_WITH_GROUP)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_sameNameExist_finalFallback() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx231 = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx231 = { group = "fake.group2", name = "fake.name2", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx231" }
    """)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify avoiding the same name with different module
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx231 = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx2312 = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx231 = { group = "fake.group2", name = "fake.name2", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx231" }
androidx-lifecycle-lifecycle-runtime-ktx2312 = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx2312" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_FINAL_FALLBACK)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_sameNameExist_finalFallback_secondLoop() {
    writeToVersionCatalogFile("""
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx231 = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx2312 = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx231 = { group = "fake.group2", name = "fake.name2", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx231" }
androidx-lifecycle-lifecycle-runtime-ktx2312 = { group = "fake.group3", name = "fake.name3", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx2312" }
    """)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify avoiding the same name with different module.
    // The final fallback loop when picking the name in the catalog goes into the second loop
    verifyFileContents(myVersionCatalogFile, """
[versions]
lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx231 = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx2312 = "2.3.1"
androidx-lifecycle-lifecycle-runtime-ktx2313 = "2.3.1"
[libraries]
lifecycle-runtime-ktx = { group = "group", name = "name", version.ref = "lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx = { group = "fake.group", name = "fake.name", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx" }
androidx-lifecycle-lifecycle-runtime-ktx231 = { group = "fake.group2", name = "fake.name2", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx231" }
androidx-lifecycle-lifecycle-runtime-ktx2312 = { group = "fake.group3", name = "fake.name3", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx2312" }
androidx-lifecycle-lifecycle-runtime-ktx2313 = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle-lifecycle-runtime-ktx2313" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_FINAL_FALLBACK_SECOND_LOOP)
  }

  @Test
  fun testAddPlatformDependencyWithVersionCatalog() {
    recipeExecutor.addPlatformDependency("androidx.compose:compose-bom:2022.10.00")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
compose-bom = "2022.10.00"
[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
    """)
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_PLATFORM_DEPENDENCY)
  }

  @Test
  fun testGetExtVar_valueFound() {
    writeToProjectBuildFile(TestFile.GET_EXT_VAR_INITIAL)

    val version = recipeExecutor.getExtVar("wear_compose_version", "1.0.0")

    assertEquals("3.0.0", version)
  }

  @Test
  fun testGetExtVar_valueNotFound() {
    writeToProjectBuildFile(TestFile.GET_EXT_VAR_INITIAL)

    val version = recipeExecutor.getExtVar("fake_variable", "1.0.0")

    assertEquals("1.0.0", version)
  }

  enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    VERSION_CATALOG_ADD_DEPENDENCY("versionCatalogAddDependency"),
    VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME("versionCatalogAddDependencyAvoidSameName"),
    VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_WITH_GROUP("versionCatalogAddDependencyAvoidSameNameWithGroup"),
    VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_FINAL_FALLBACK("versionCatalogAddDependencyAvoidSameNameFinalFallback"),
    VERSION_CATALOG_ADD_DEPENDENCY_AVOID_SAME_NAME_FINAL_FALLBACK_SECOND_LOOP("versionCatalogAddDependencyAvoidSameNameFinalFallbackSecondLoop"),
    VERSION_CATALOG_ADD_PLATFORM_DEPENDENCY("versionCatalogAddPlatformDependency"),
    GET_EXT_VAR_INITIAL("getExtVarInitial"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/defaultRecipeExecutor/$path", extension)
    }
  }
}