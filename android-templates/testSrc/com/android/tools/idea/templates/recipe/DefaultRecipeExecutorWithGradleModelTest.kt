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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.android.tools.idea.lint.common.getModuleDir
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.SystemDependent
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DefaultRecipeExecutorWithGradleModelTest : GradleFileModelTestCase("tools/adt/idea/android-templates/testData/recipe") {

  private val mockProjectTemplateData = mock<ProjectTemplateData>()
  private val mockModuleTemplateData = mock<ModuleTemplateData>()

  private val EMPTY_SETTINGS_CONTENT = """
     pluginManagement {
       plugins {
       }
     }
    """.trimIndent()
  private val EMPTY_BUILD_CONTENT = """
   buildscript {
     dependencies {
     }
   }
    """.trimIndent()

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
    DefaultRecipeExecutor(renderingContext)
  }

  @Before
  fun init() {
    DeclarativeIdeSupport.override(false)
    DeclarativeStudioSupport.override(false)
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)
    whenever(mockProjectTemplateData.agpVersion).thenReturn(AgpVersion.parse("8.0.0"))
  }

  @After
  fun cleanup() {
    DeclarativeIdeSupport.clearOverride()
    DeclarativeStudioSupport.clearOverride()
  }

  private fun deleteVersionCatalogFile() {
    ApplicationManager.getApplication().runWriteAction {
      myVersionCatalogFile.delete("test")
    }
  }

  /**
   * Tests that a project which did not already use Version Catalog can still have a dependency added to it.
   */
  @Test
  fun testAddDependencyWithoutVersionCatalog() {
    deleteVersionCatalogFile()
    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myBuildFile, TestFile.NO_VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog() {
    writeToVersionCatalogFile("""
      [versions]
      [libraries]
      """.trimIndent())
    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
      [versions]
      lifecycleRuntimeKtx = "2.3.1"
      [libraries]
      androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
    """.trimIndent())
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_alreadyExists() {
    writeToVersionCatalogFile("""
      [versions]
      lifecycle-runtime-ktx = "2.3.1"
      [libraries]
      androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """.trimIndent())
    writeToBuildFile(TestFile.VERSION_CATALOG_ADD_DEPENDENCY)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify library is not duplicated in the toml file and the build file
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      lifecycle-runtime-ktx = "2.3.1"
      [libraries]
      androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """.trimIndent())
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddDependencyWithVersionCatalog_alreadyExists_asModuleRepresentation() {
    writeToVersionCatalogFile("""
      [versions]
      lifecycle-runtime-ktx = "2.3.1"
      [libraries]
      androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """.trimIndent())
    writeToBuildFile(TestFile.VERSION_CATALOG_ADD_DEPENDENCY)

    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Verify library is not duplicated in the toml file and the build file
    verifyFileContents(myVersionCatalogFile, """
      [versions]
      lifecycle-runtime-ktx = "2.3.1"
      [libraries]
      androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
    """.trimIndent())
    verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_ADD_DEPENDENCY)
  }

  @Test
  fun testAddPlatformDependencyWithVersionCatalog() {
    writeToVersionCatalogFile("""
      [versions]
      [libraries]
      """.trimIndent())
    recipeExecutor.addPlatformDependency("androidx.compose:compose-bom:2022.10.00")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
      [versions]
      composeBom = "2022.10.00"
      [libraries]
      androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
    """.trimIndent())
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

  /**
   * Tests that a project which did not already use Version Catalog can still have a plugin added to it.
   */
  @Test
  fun testApplyKotlinPluginWithoutVersionCatalog() {
    deleteVersionCatalogFile()

    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)

    recipeExecutor.applyPlugin("org.jetbrains.kotlin.android", "1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(mySettingsFile, TestFile.NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_SETTING_FILE)
    verifyFileContents(myBuildFile, TestFile.NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testAddKotlinPluginToPluginManagement() {
    deleteVersionCatalogFile()

    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)

    recipeExecutor.addPlugin("org.jetbrains.kotlin.android", "org.jetbrains.kotlin:kotlin-gradle-plugin","1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(mySettingsFile, TestFile.NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_SETTING_FILE)
    verifyFileContents(myBuildFile, TestFile.NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testAddKotlinPluginWithClasspath() {
    deleteVersionCatalogFile()
    writeToSettingsFile("")
    writeToBuildFile(EMPTY_BUILD_CONTENT)
    recipeExecutor.addPlugin("org.jetbrains.kotlin.android", "org.jetbrains.kotlin:kotlin-gradle-plugin","1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(mySettingsFile, "")
    verifyFileContents(myBuildFile, TestFile.NO_VERSION_CATALOG_ADD_KOTLIN_PLUGIN_CLASSPATH)
  }

  @Ignore("b/388555862")
  @Test
  fun testAddKotlinPluginToPluginSection() {
    deleteVersionCatalogFile()
    writeToSettingsFile("")
    writeToBuildFile("""
      plugins {
      }
    """.trimIndent())
    recipeExecutor.addPlugin("org.jetbrains.kotlin.android", "org.jetbrains.kotlin:kotlin-gradle-plugin","1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(mySettingsFile, "")
    verifyFileContents(myBuildFile, TestFile.NO_VERSION_CATALOG_ADD_KOTLIN_PLUGIN_TO_PLUGINS_BLOCK)
  }


  @Test
  fun testApplyKotlinPluginWithVersionCatalog() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)

    recipeExecutor.applyPlugin("org.jetbrains.kotlin.android", "1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
kotlin = "1.7.20"
[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
    """)
    verifyFileContents(mySettingsFile, EMPTY_SETTINGS_CONTENT)
    verifyFileContents(myBuildFile, TestFile.APPLY_KOTLIN_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testApplyKotlinPluginWithVersionCatalog_sameVersionNameExists() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    writeToVersionCatalogFile("""
[versions]
kotlin = "100"
[libraries]
[plugins]
fake-plugin = { id = "fake.plugin", version.ref = "kotlin" }
    """)

    recipeExecutor.applyPlugin("org.jetbrains.kotlin.android", "1.7.20")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
kotlin = "100"
kotlinVersion = "1.7.20"
[libraries]
[plugins]
fake-plugin = { id = "fake.plugin", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlinVersion" }
    """)
    verifyFileContents(mySettingsFile, EMPTY_SETTINGS_CONTENT)
    verifyFileContents(myBuildFile, TestFile.APPLY_KOTLIN_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testApplyNonCommonPlugin() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)

    recipeExecutor.applyPlugin("not.common.plugin", "1.0.2")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
not = "1.0.2"
[plugins]
not = { id = "not.common.plugin", version.ref = "not" }
    """)
    verifyFileContents(mySettingsFile, EMPTY_SETTINGS_CONTENT)
    verifyFileContents(myBuildFile, TestFile.APPLY_NOT_COMMON_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testAddAgpPlugin_noAgpPluginHasNotDeclared() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    writeToVersionCatalogFile("""
[versions]
[libraries]
[plugins]
    """)

    recipeExecutor.applyPlugin("com.android.application", "8.0.0")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
    """)
    verifyFileContents(mySettingsFile, EMPTY_SETTINGS_CONTENT)
    verifyFileContents(myBuildFile, TestFile.APPLY_AGP_PLUGIN_BUILD_FILE)
  }

  @Test
  fun testAddAgpPlugin_samePluginNameExists() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    writeToVersionCatalogFile("""
[versions]
fake = "100"
[libraries]
[plugins]
android-application = { id = "fake.plugin", version.ref = "fake" }
    """)

    recipeExecutor.applyPlugin("com.android.application", "8.0.0")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
fake = "100"
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "fake.plugin", version.ref = "fake" }
com-android-application = { id = "com.android.application", version.ref = "agp" }
    """)
    verifyFileContents(mySettingsFile, EMPTY_SETTINGS_CONTENT)
    verifyFileContents(myBuildFile, TestFile.APPLY_AGP_PLUGIN_WITH_REVISION_BUILD_FILE)
  }

  @Test
  fun testAddAgpPlugin_anotherAgpPluginHasDeclared() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    writeToVersionCatalogFile("""
[versions]
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
    """)

    recipeExecutor.applyPlugin("com.android.library", "8.0.0")

    applyChanges(recipeExecutor.projectBuildModel!!)

    verifyFileContents(myVersionCatalogFile, """
[versions]
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
    """)
  }

  @Test
  fun testAddAgpPlugin_anotherAgpPluginHasDeclaredInDifferentVersion() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    writeToVersionCatalogFile("""
[versions]
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
    """)

    // Apply a plugin with the different version from the existing agp version in the catalog
    recipeExecutor.applyPlugin("com.android.library", "8.0.0-beta04")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Existing agp version is respected
    verifyFileContents(myVersionCatalogFile, """
[versions]
agp = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
    """)
  }

  @Test
  fun testAddAgpPlugin_anotherAgpPluginHasDeclared_withDifferentNameFromDefault() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    // Another AGP plugin is declared with the version name different from the default
    writeToVersionCatalogFile("""
[versions]
agp-version = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp-version" }
    """)

    recipeExecutor.applyPlugin("com.android.library", "8.0.0-beta04")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // The new declared plugin will use the same version name as the existing one
    verifyFileContents(myVersionCatalogFile, """
[versions]
agp-version = "8.0.0"
[libraries]
[plugins]
android-application = { id = "com.android.application", version.ref = "agp-version" }
android-library = { id = "com.android.library", version.ref = "agp-version" }
    """)
  }

  @Test
  fun testAddAgpPlugin_anotherAgpPluginHasDeclared_inLiteral() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    // Existing AGP plugin's version is written as a string literal
    writeToVersionCatalogFile("""
[versions]
[libraries]
[plugins]
android-application = { id = "com.android.application", version = "8.0.0" }
    """)

    recipeExecutor.applyPlugin("com.android.library", "8.0.0-beta04")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Version in literal isn't touched. The new version entry named "agp" is created instead.
    verifyFileContents(myVersionCatalogFile, """
[versions]
agp = "8.0.0-beta04"
[libraries]
[plugins]
android-application = { id = "com.android.application", version = "8.0.0" }
android-library = { id = "com.android.library", version.ref = "agp" }
    """)
  }

  @Test
  fun testAddAgpPlugin_differentPluginUseDefaultNameForAgp() {
    writeToSettingsFile(EMPTY_SETTINGS_CONTENT)
    // Another plugin already use the default name ("agp") for AGP plugins
    writeToVersionCatalogFile("""
[versions]
agp = "1.0.0"
[libraries]
[plugins]
fake-plugin = { id = "fake.plugin", version.ref = "agp" }
    """)

    recipeExecutor.applyPlugin("com.android.library", "8.0.0")

    applyChanges(recipeExecutor.projectBuildModel!!)

    // Different name is picked for the declared AGP plugin
    verifyFileContents(myVersionCatalogFile, """
[versions]
agp = "1.0.0"
agpVersion = "8.0.0"
[libraries]
[plugins]
fake-plugin = { id = "fake.plugin", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agpVersion" }
    """)
  }

  enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    NO_VERSION_CATALOG_ADD_DEPENDENCY("noVersionCatalogAddDependency"),
    VERSION_CATALOG_ADD_DEPENDENCY("versionCatalogAddDependency"),
    VERSION_CATALOG_ADD_PLATFORM_DEPENDENCY("versionCatalogAddPlatformDependency"),
    GET_EXT_VAR_INITIAL("getExtVarInitial"),
    NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_BUILD_FILE("noVersionCatalogApplyKotlinPlugin"),
    NO_VERSION_CATALOG_ADD_KOTLIN_PLUGIN_CLASSPATH("noVersionCatalogAddKotlinPlugin"),
    NO_VERSION_CATALOG_ADD_KOTLIN_PLUGIN_TO_PLUGINS_BLOCK("noVersionCatalogAddKotlinPluginToPlugins"),
    NO_VERSION_CATALOG_APPLY_KOTLIN_PLUGIN_SETTING_FILE("noVersionCatalogApplyKotlinPlugin.settings"),
    APPLY_KOTLIN_PLUGIN_BUILD_FILE("versionCatalogApplyKotlinPlugin"),
    APPLY_NOT_COMMON_PLUGIN_BUILD_FILE("versionCatalogApplyNotCommonPlugin"),
    APPLY_AGP_PLUGIN_BUILD_FILE("versionCatalogApplyAgpPlugin"),
    APPLY_AGP_PLUGIN_WITH_REVISION_BUILD_FILE("versionCatalogApplyAgpPluginWithRevision"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/defaultRecipeExecutor/$path", extension)
    }
  }
}