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
import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
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
import com.android.tools.idea.wizard.template.DslLanguage
import com.android.tools.idea.wizard.template.DslLanguage.GROOVY
import com.android.tools.idea.wizard.template.DslLanguage.KTS
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * This is deliberately un-parameterized to split Groovy vs KTS into different shards, since the overhead from Gradle sync with different
 * types causes timeouts
 */
class GroovyAddNewModulesToAppTest : AddNewModulesToAppTest(GROOVY, false)

class GroovyVersionCatalogAddNewModulesToAppTest : AddNewModulesToAppTest(GROOVY, true)

class KtsAddNewModulesToAppTest : AddNewModulesToAppTest(KTS, false)

class KtsVersionCatalogAddNewModulesToAppTest : AddNewModulesToAppTest(KTS, true)

abstract class AddNewModulesToAppTest(private val dslLanguage: DslLanguage, private val useVersionCatalog: Boolean) {
  @get:Rule val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion())

  // Ignore project sync (to speed up test), if later we are going to perform a gradle build anyway.
  private val emptyProjectSyncInvoker =
    object : ProjectSyncInvoker {
      override fun syncProject(project: Project) {}
    }

  private fun loadInitialProject() {
    // todo b/440101998
    val disableAndroidX: (File) -> Unit = { File(it, "gradle.properties").appendText("\n\nandroid.useAndroidX=false") }
    if (useVersionCatalog) {
      projectRule.load(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG, agpVersion = getAgpVersion(), preLoad = disableAndroidX)
    } else {
      projectRule.load(TestProjectPaths.SIMPLE_APPLICATION, agpVersion = getAgpVersion(), preLoad = disableAndroidX)
    }
  }

  @Test
  fun addNewDynamicFeatureModule() {
    loadInitialProject()

    val project = projectRule.project
    createDefaultDynamicFeatureModel(project, "feature1", project.findAppModule(), dslLanguage, emptyProjectSyncInvoker)
    checkAgpClasspathAndId("feature1", "com.android.dynamic-feature", "libs.plugins.android.dynamic.feature")
    checkModuleCompileSdkVersion("feature1")
    assembleDebugProject()
  }

  @Test
  @Ignore("b/447536147")
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
    generateModuleFiles(project, baseModuleModel, "base", KTS) // Base module is always kts for this test

    val baseModule = project.findModule("base")
    createDefaultDynamicFeatureModel(project, "feature1", baseModule, dslLanguage, emptyProjectSyncInvoker)
    createDefaultDynamicFeatureModel(project, "feature2", baseModule, dslLanguage, emptyProjectSyncInvoker)

    checkBuildGradleJavaVersion("feature1")
    checkModuleCompileSdkVersion("feature1")
    checkBuildGradleJavaVersion("feature2")
    checkModuleCompileSdkVersion("feature2")
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
    generateModuleFiles(project, libModuleModel, moduleName, dslLanguage) // Base module is always kts for this test

    checkAgpClasspathAndId("mylibrary", "com.android.library", "libs.plugins.android.library")
    checkModuleCompileSdkVersion("mylibrary")
    checkBuildGradleJavaVersion(moduleName)
    assembleDebugProject()
  }

  @Test
  fun addNewAndroidLibraryModuleDoesNotContainProguardFiles() {
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
    generateModuleFiles(project, libModuleModel, moduleName, dslLanguage)

    checkBuildGradleNoProguardFiles(moduleName)
    assembleDebugProject()
  }

  @Test
  fun addNewAndroidApplicationModuleContainsProguardFiles() {
    loadInitialProject()

    val project = projectRule.project
    val appModuleModel =
      NewAndroidModuleModel.fromExistingProject(
        project = project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
        formFactor = FormFactor.Mobile,
        category = Category.Activity,
        isLibrary = false,
      )
    val moduleName = "myapp"
    generateModuleFiles(project, appModuleModel, moduleName, dslLanguage)

    checkBuildGradleHasProguardFiles(moduleName)
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
    generateModuleFiles(project, libModuleModel, module, dslLanguage)

    checkBuildGradleJavaVersion(module)
    assembleDebugProject()

    // checking plugin/classpath inserted in correct places
    assertTrue(
      File(project.basePath!!).resolve("build.gradle").readText().contains("classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:")
    )
    val pluginId = "org.jetbrains.kotlin.jvm"
    // settings must have no declared plugins
    assertFalse(File(project.basePath!!).resolve("settings.gradle").readText().contains("kotlin"))
    if (dslLanguage.isKts) {
      assertTrue(File(project.basePath!!).resolve(module).resolve("build.gradle.kts").readText().contains("id(\"$pluginId\")\n"))
    } else {
      assertTrue(File(project.basePath!!).resolve(module).resolve("build.gradle").readText().contains("id '$pluginId'\n"))
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
      File(project.basePath!!).resolve("build.gradle").readText().contains("alias($pluginAlias) apply false")
    }

    if (dslLanguage.isKts) {
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
    if (dslLanguage.isKts) {
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

  private fun checkModuleCompileSdkVersion(moduleName: String) {
    val project = projectRule.project
    val buildGradleFileName = "build.gradle"
    val text =
      if (dslLanguage.isKts) {
        File(project.basePath!!).resolve(moduleName).resolve("$buildGradleFileName.kts").readText()
      } else {
        File(project.basePath!!).resolve(moduleName).resolve(buildGradleFileName).readText()
      }
    assertTrue(
      text
        .removeSpaces()
        .contains(
          """
            compileSdk {
              version = release(${getAgpVersion().compileSdk})
            }
          """
            .trimIndent()
            .removeSpaces()
        )
    )
  }

  private fun readBuildFile(moduleName: String): String {
    val project = projectRule.project
    val buildGradleFileName = if (dslLanguage.isKts) "build.gradle.kts" else "build.gradle"
    val text = File(project.basePath!!).resolve(moduleName).resolve(buildGradleFileName).readText()
    return text
  }

  private fun checkBuildGradleNoProguardFiles(moduleName: String) {
    val text = readBuildFile(moduleName)
    assertFalse("Generated build.gradle should not contain 'proguardFiles'", text.contains("proguardFiles"))
    assertFalse("Generated build.gradle should not contain 'getDefaultProguardFile'", text.contains("getDefaultProguardFile"))
    assertTrue("Generated build.gradle for library should contain 'consumerProguardFiles'", text.contains("consumerProguardFiles"))
  }

  private fun checkBuildGradleHasProguardFiles(moduleName: String) {
    val text = readBuildFile(moduleName)
    assertTrue("Generated build.gradle should contain 'proguardFiles'", text.contains("proguardFiles"))
    assertTrue("Generated build.gradle should contain 'getDefaultProguardFile'", text.contains("getDefaultProguardFile"))
    assertFalse("Generated build.gradle for application should not contain 'consumerProguardFiles'", text.contains("consumerProguardFiles"))
  }
}

private fun String.removeSpaces() = replace("[ \\r\\t]+".toRegex(), "")

private fun createDefaultDynamicFeatureModel(
  project: Project,
  moduleName: String,
  baseModule: Module,
  dslLanguage: DslLanguage,
  projectSyncInvoker: ProjectSyncInvoker,
) {
  val model =
    DynamicFeatureModel(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = projectSyncInvoker,
      templateName = "Dynamic Feature",
      templateDescription = "Dynamic Feature description",
    )
  model.baseApplication.value = baseModule // Dynamic Feature base module
  generateModuleFiles(project, model, moduleName, dslLanguage)
}

private fun generateModuleFiles(project: Project, model: ModuleModel, moduleName: String, dslLanguage: DslLanguage) {
  model.androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(HIGHEST_KNOWN_STABLE_API)
  model.moduleName.set(moduleName)
  model.template.set(createDefaultModuleTemplate(project, moduleName))
  model.packageName.set("com.example")
  model.dslLanguage.set(dslLanguage)

  model.handleFinished() // Generate module files
}
