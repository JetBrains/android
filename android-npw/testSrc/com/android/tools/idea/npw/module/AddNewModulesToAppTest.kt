/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.tools.idea.npw.dynamicapp.DynamicFeatureModel
import com.android.tools.idea.npw.java.NewLibraryModuleModel
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate.createDefaultModuleTemplate
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddNewModulesToAppTest(
  private val useGradleKts: Boolean,
  private val useVersionCatalog: Boolean,
) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGradleKts={0}, useVersionCatalog = {1}")
    fun data() =
      listOf(arrayOf(false, false), arrayOf(false, true), arrayOf(true, false), arrayOf(true, true))
  }

  @get:Rule val projectRule = AndroidGradleProjectRule()

  // Ignore project sync (to speed up test), if later we are going to perform a gradle build anyway.
  private val emptyProjectSyncInvoker =
    object : ProjectSyncInvoker {
      override fun syncProject(project: Project) {}
    }

  private fun loadInitialProject() {
    if (useVersionCatalog) {
      projectRule.load(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG)
    } else {
      projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)
    }
  }

  @Test
  fun addNewDynamicFeatureModule() {
    loadInitialProject()

    val project = projectRule.project
    createDefaultDynamicFeatureModel(
      project,
      "feature1",
      project.findAppModule(),
      useGradleKts,
      emptyProjectSyncInvoker,
    )
    checkAgpClasspathAndId(
      "feature1",
      "com.android.dynamic-feature",
      "libs.plugins.android.dynamic.feature",
    )
    assembleDebugProject()
  }

  @Test
  fun addMultipleDynamicFeatureModulesToKtsBaseModule() {
    loadInitialProject()
    val project = projectRule.project

    val baseModuleModel =
      NewAndroidModuleModel.fromExistingProject(
        project = project,
        moduleParent = ":",
        projectSyncInvoker = DefaultProjectSyncInvoker(),
        formFactor = FormFactor.Mobile,
        category = Category.Activity,
      )
    generateModuleFiles(
      project,
      baseModuleModel,
      "base",
      useGradleKts = true,
    ) // Base module is always kts for this test

    val baseModule = project.findModule("base")
    createDefaultDynamicFeatureModel(
      project,
      "feature1",
      baseModule,
      useGradleKts,
      emptyProjectSyncInvoker,
    )
    createDefaultDynamicFeatureModel(
      project,
      "feature2",
      baseModule,
      useGradleKts,
      emptyProjectSyncInvoker,
    )

    checkBuildGradleJavaVersion("feature1")
    checkBuildGradleJavaVersion("feature2")
    assembleDebugProject()
  }

  @Test
  fun addNewAndroidLibraryModule() {
    loadInitialProject()

    val project = projectRule.project
    val libModuleModel =
      NewAndroidModuleModel.fromExistingProject(
        project = project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
        formFactor = FormFactor.Mobile,
        category = Category.Activity,
        isLibrary = true,
      )
    val moduleName = "mylibrary"
    generateModuleFiles(
      project,
      libModuleModel,
      moduleName,
      useGradleKts,
    ) // Base module is always kts for this test

    checkAgpClasspathAndId("mylibrary", "com.android.library", "libs.plugins.android.library")
    checkBuildGradleJavaVersion(moduleName)
    assembleDebugProject()
  }

  @Test
  fun addNewPureLibraryModuleInKotlinHasJvmCompatibility() {
    if (useVersionCatalog) {
      // TODO (b/369979748): Kotlin version mismatch with Version Catalog
      return
    }

    loadInitialProject()

    val project = projectRule.project
    val module = "mylibrary"
    val libModuleModel = NewLibraryModuleModel(project, ":", emptyProjectSyncInvoker)
    libModuleModel.language.set(Optional.of(Language.Kotlin))
    generateModuleFiles(project, libModuleModel, module, useGradleKts)

    checkBuildGradleJavaVersion(module)
    assembleDebugProject()

    // checking plugin/classpath inserted in correct places
    assertTrue(
      File(project.basePath!!)
        .resolve("build.gradle")
        .readText()
        .contains("classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:")
    )
    val pluginId = "org.jetbrains.kotlin.jvm"
    // settings must have no declared plugins
    assertFalse(File(project.basePath!!).resolve("settings.gradle").readText().contains("kotlin"))
    if (useGradleKts) {
      assertTrue(
        File(project.basePath!!)
          .resolve(module)
          .resolve("build.gradle.kts")
          .readText()
          .contains("id(\"$pluginId\")\n")
      )
    } else {
      assertTrue(
        File(project.basePath!!)
          .resolve(module)
          .resolve("build.gradle")
          .readText()
          .contains("id '$pluginId'\n")
      )
    }

    // Also run :mylibrary:compileKotlin to ensure there is no JVM target compatibility issue,
    // because assembling just the project doesn't trigger this error
    projectRule.invokeTasks(":mylibrary:compileKotlin").apply {
      buildError?.printStackTrace()
      assertTrue("Library didn't compile correctly", isBuildSuccessful)
    }
  }

  private fun assembleDebugProject() {
    projectRule.invokeTasks("assembleDebug").apply {
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }

  private fun checkAgpClasspathAndId(moduleName: String, pluginId: String, pluginAlias: String) {
    val project = projectRule.project
    if (useVersionCatalog) {
      File(project.basePath!!)
        .resolve("build.gradle")
        .readText()
        .contains("alias($pluginAlias) apply false")
    }

    if (useGradleKts) {
      assertTrue(
        File(project.basePath!!)
          .resolve(moduleName)
          .resolve("build.gradle.kts")
          .readText()
          .contains(if (useVersionCatalog) "alias($pluginAlias)" else "id(\"$pluginId\")\n")
      )
    } else {
      assertTrue(
        File(project.basePath!!)
          .resolve(moduleName)
          .resolve("build.gradle")
          .readText()
          .contains(if (useVersionCatalog) "alias($pluginAlias)" else "id '$pluginId'\n")
      )
    }
  }

  private fun checkBuildGradleJavaVersion(moduleName: String) {
    val project = projectRule.project
    if (useGradleKts) {
      assertTrue(
        File(project.basePath!!)
          .resolve(moduleName)
          .resolve("build.gradle.kts")
          .readText()
          .contains("sourceCompatibility = JavaVersion.VERSION_11")
      )
    } else {
      assertTrue(
        File(project.basePath!!)
          .resolve(moduleName)
          .resolve("build.gradle")
          .readText()
          .contains("sourceCompatibility JavaVersion.VERSION_11")
      )
    }
  }
}

private fun createDefaultDynamicFeatureModel(
  project: Project,
  moduleName: String,
  baseModule: Module,
  useGradleKts: Boolean,
  projectSyncInvoker: ProjectSyncInvoker,
) {
  val model =
    DynamicFeatureModel(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = projectSyncInvoker,
      isInstant = false,
      templateName = "Dynamic Feature",
      templateDescription = "Dynamic Feature description",
    )
  model.baseApplication.value = baseModule // Dynamic Feature base module
  generateModuleFiles(project, model, moduleName, useGradleKts)
}

private fun generateModuleFiles(
  project: Project,
  model: ModuleModel,
  moduleName: String,
  useGradleKts: Boolean,
) {
  model.androidSdkInfo.value =
    AndroidVersionsInfo.VersionItem.fromStableVersion(HIGHEST_KNOWN_STABLE_API)
  model.moduleName.set(moduleName)
  model.template.set(createDefaultModuleTemplate(project, moduleName))
  model.packageName.set("com.example")
  model.useGradleKts.set(useGradleKts)

  model.handleFinished() // Generate module files
}
