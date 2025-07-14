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
package com.android.tools.idea.gradle.project.sync.issues.processor.runsGradleErrors

import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.internal.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.gradle.project.sync.issues.processor.AddComposeCompilerGradlePluginProcessor
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PLUGINS_DSL
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_ADD_COMPOSE_COMPILER_GRADLE_PLUGIN
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [AddComposeCompilerGradlePluginProcessor]
 */
class AddComposeCompilerGradlePluginProcessorTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()
  val project by lazy { projectRule.project }

  /**
   * Test case when the Compose Compiler Gradle plugin is added to the buildscript classpath
   */
  @Test
  fun testBuildscriptClasspath() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    val module = project.findAppModule()
    val processor =
      AddComposeCompilerGradlePluginProcessor(project, listOf(module), KOTLIN_VERSION_FOR_TESTS)

    // Check that the plugin has not already been added to buildscript classpath
    var projectBuildModel = ProjectBuildModel.get(project)
    var pluginFromBuildscriptClasspath =
      getPluginFromBuildscriptClasspath(projectBuildModel, KOTLIN_VERSION_FOR_TESTS)
    assertThat(pluginFromBuildscriptClasspath).isNull()
    // Check that the plugin has not already been applied to the app module
    var pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNull()

    // Run processor and verify expected usages
    runProcessor(processor)

    // Assert that the dependency was added to the buildscript classpath
    projectBuildModel = ProjectBuildModel.get(project)
    pluginFromBuildscriptClasspath =
      getPluginFromBuildscriptClasspath(projectBuildModel, KOTLIN_VERSION_FOR_TESTS)
    assertThat(pluginFromBuildscriptClasspath).isNotNull()
    // Assert that the plugin was applied to the app module
    pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNotNull()
  }

  /**
   * Test case when the Compose Compiler Gradle plugin is added to the root build file's plugins
   * block.
   */
  @Test
  fun testPluginsBlock() {
    projectRule.loadProject(SIMPLE_APPLICATION_PLUGINS_DSL)

    val module = project.findAppModule()
    val processor =
      AddComposeCompilerGradlePluginProcessor(project, listOf(module), KOTLIN_VERSION_FOR_TESTS)

    // Check that the plugin has not already been added to the plugins block
    var projectBuildModel = ProjectBuildModel.get(project)
    var pluginFromPluginsBlock = getPluginFromPluginsBlock(projectBuildModel)
    assertThat(pluginFromPluginsBlock).isNull()
    // Check that the plugin has not already been applied to the app module
    var pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNull()

    // Run processor and verify expected usages
    runProcessor(processor)

    // Assert that the dependency was added to the plugins block
    projectBuildModel = ProjectBuildModel.get(project)
    pluginFromPluginsBlock =
      getPluginFromPluginsBlock(projectBuildModel, KOTLIN_VERSION_FOR_TESTS)
    assertThat(pluginFromPluginsBlock).isNotNull()
    // Assert that the plugin was applied to the app module
    pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNotNull()
  }

  /**
   * Test case when the Compose Compiler Gradle plugin is added to the root build file's plugins
   * block and version catalog is used.
   */
  @Test
  fun testPluginsBlockWithVersionCatalog() {
    projectRule.loadProject(SIMPLE_APPLICATION_VERSION_CATALOG)

    val module = project.findAppModule()
    val processor =
      AddComposeCompilerGradlePluginProcessor(project, listOf(module), KOTLIN_VERSION_FOR_TESTS)

    // Check that the plugin has not already been added to the plugins block
    var projectBuildModel = ProjectBuildModel.get(project)
    var pluginsFromPluginsBlock = getPluginFromPluginsBlock(projectBuildModel)
    assertThat(pluginsFromPluginsBlock).isNull()
    // Check that the plugin has not already been applied to the app module
    var pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNull()
    // Check that the plugin has not already been added to the version catalog
    checkVersionCatalog(
      projectBuildModel,
      expectedKotlinVersion = null,
      shouldHaveComposeCompilerGradlePlugin = false
    )

    // Run processor and verify expected usages
    runProcessor(processor)

    // Assert that the dependency was added to the plugins block
    projectBuildModel = ProjectBuildModel.get(project)
    pluginsFromPluginsBlock = getPluginFromPluginsBlock(projectBuildModel)
    assertThat(pluginsFromPluginsBlock).isNotNull()
    // Assert that the plugin was applied to the app module
    pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNotNull()
    // Assert that the version catalog has been updated
    checkVersionCatalog(
      projectBuildModel,
      expectedKotlinVersion = KOTLIN_VERSION_FOR_TESTS,
      shouldHaveComposeCompilerGradlePlugin = true
    )
  }

  /**
   * Test case when the Compose Compiler Gradle plugin is added to the root settings file's
   * pluginManagement block.
   */
  @Test
  fun testPluginManagementBlock() {
    projectRule.loadProject(SIMPLE_APPLICATION_PLUGINS_DSL)

    // Add pluginManagement.plugins block
    var projectBuildModel = ProjectBuildModel.get(project)
    projectBuildModel.projectSettingsModel!!
      .pluginManagement()
      .plugins()
      .applyPlugin("org.jetbrains.kotlin.android", KOTLIN_VERSION_FOR_TESTS, apply = false)
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
    }

    val module = project.findAppModule()
    val processor =
      AddComposeCompilerGradlePluginProcessor(project, listOf(module), KOTLIN_VERSION_FOR_TESTS)

    // Check that the plugin has not already been added to the pluginManagement block
    projectBuildModel = ProjectBuildModel.get(project)
    var pluginFromPluginManagement = getPluginFromPluginManagementBlock(projectBuildModel)
    assertThat(pluginFromPluginManagement).isNull()
    // Check that the plugin has not already been applied to the app module
    var pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNull()

    // Run processor and verify expected usages
    runProcessor(processor)

    // Assert that the dependency was added to the pluginManagement block
    projectBuildModel = ProjectBuildModel.get(project)
    pluginFromPluginManagement =
      getPluginFromPluginManagementBlock(projectBuildModel, KOTLIN_VERSION_FOR_TESTS)
    assertThat(pluginFromPluginManagement).isNotNull()
    // Assert that the plugin was applied to the app module
    pluginFromModule =
      projectBuildModel.getModuleBuildModel(module)?.let { getPluginFromModule(it) }
    assertThat(pluginFromModule).isNotNull()
  }

  /**
   * Run the processor and verify expected usages
   */
  private fun runProcessor(processor: AddComposeCompilerGradlePluginProcessor) {
    // Verify expected usages
    val usages = runReadAction { processor.findUsages() }
    assertThat(usages).hasLength(1)

    // Perform refactoring
    var synced = false
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        synced = true
      }
    }, projectRule.fixture.testRootDisposable)
    WriteCommandAction.runWriteCommandAction(project) {
      processor.updateProjectBuildModel()
    }
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_ADD_COMPOSE_COMPILER_GRADLE_PLUGIN)

    // Assert that sync succeeded
    assertThat(synced).isTrue()
  }

  /**
   * Return the Compose Compiler Gradle plugin model if it's been added to the
   * top-level plugins block, or null if not
   */
  private fun getPluginFromPluginsBlock(projectBuildModel: ProjectBuildModel): PluginModel? =
    projectBuildModel.projectBuildModel!!
      .plugins()
      .firstOrNull { it.name().valueAsString() == "org.jetbrains.kotlin.plugin.compose" }

  /**
   * Return the Compose Compiler Gradle plugin model if it's been added to the
   * top-level plugins block with the given version, or null if not
   */
  private fun getPluginFromPluginsBlock(
    projectBuildModel: ProjectBuildModel,
    version: String
  ): PluginModel? =
    projectBuildModel.projectBuildModel!!
      .plugins()
      .firstOrNull {
        it.name().valueAsString() == "org.jetbrains.kotlin.plugin.compose" &&
        it.version().valueAsString() == version
      }

  /**
   * Return the Compose Compiler Gradle plugin model if it's been added to the
   * pluginManagement block, or null if not
   */
  private fun getPluginFromPluginManagementBlock(
    projectBuildModel: ProjectBuildModel
  ): PluginModel? =
    projectBuildModel.projectSettingsModel!!
      .pluginManagement()
      .plugins()
      .plugins()
      .firstOrNull { it.name().valueAsString() == "org.jetbrains.kotlin.plugin.compose" }

  /**
   * Return the Compose Compiler Gradle plugin model if it's been added to the
   * pluginManagement block with the given version, or null if not
   */
  private fun getPluginFromPluginManagementBlock(
    projectBuildModel: ProjectBuildModel,
    version: String
  ): PluginModel? =
    projectBuildModel.projectSettingsModel!!
      .pluginManagement()
      .plugins()
      .plugins()
      .firstOrNull {
        it.name().valueAsString() == "org.jetbrains.kotlin.plugin.compose" &&
        it.version().valueAsString() == version
      }

  /**
   * Return the Compose Compiler Gradle plugin dependency model if it's been added to the
   * buildscript classpath, or null if not
   */
  private fun getPluginFromBuildscriptClasspath(
    projectBuildModel: ProjectBuildModel,
    version: String
  ): ArtifactDependencyModel? =
    projectBuildModel.projectBuildModel!!
      .buildscript()
      .dependencies()
      .artifacts("classpath")
      .firstOrNull {
        it.group().valueAsString() == "org.jetbrains.kotlin" &&
        it.name().valueAsString() == "compose-compiler-gradle-plugin" &&
        it.version().unresolvedModel.valueAsString() == version
      }

  /**
   * Return the Compose Compiler Gradle plugin model if it's applied to the module, or null if not
   */
  private fun getPluginFromModule(moduleBuildModel: GradleBuildModel): PluginModel? =
    moduleBuildModel.appliedPlugins()
      .firstOrNull { it.name().valueAsString() == "org.jetbrains.kotlin.plugin.compose" }

  /**
   * Verify version catalog
   */
  private fun checkVersionCatalog(
    projectBuildModel: ProjectBuildModel,
    expectedKotlinVersion: String?,
    shouldHaveComposeCompilerGradlePlugin: Boolean
  ) {
    val versionCatalogModel = DependenciesHelper.getDefaultCatalogModel(projectBuildModel)
    assertThat(versionCatalogModel?.versionDeclarations()?.getAll()?.get("kotlin")?.compactNotation())
      .isEqualTo(expectedKotlinVersion)
    val composeCompilerGradlePlugin =
      versionCatalogModel?.pluginDeclarations()?.getAll()?.get("kotlin-compose")
    assertThat(composeCompilerGradlePlugin != null).isEqualTo(shouldHaveComposeCompilerGradlePlugin)
  }
}