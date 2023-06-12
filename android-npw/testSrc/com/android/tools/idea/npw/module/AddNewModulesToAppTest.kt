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
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultModuleTemplate
import com.android.tools.idea.npw.dynamicapp.DynamicFeatureModel
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class AddNewModulesToAppTest(private val useGradleKts: Boolean, private val useVersionCatalog: Boolean) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGradleKts={0}, useVersionCatalog = {1}")
    fun data() = listOf(
      arrayOf(false, false),
      arrayOf(false, true),
      arrayOf(true, false),
      arrayOf(true, true),
    )
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  // Ignore project sync (to speed up test), if later we are going to perform a gradle build anyway.
  private val emptyProjectSyncInvoker = object: ProjectSyncInvoker {
    override fun syncProject(project: Project) { }
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
    createDefaultDynamicFeatureModel(project, "feature1", project.findAppModule(), useGradleKts, emptyProjectSyncInvoker)

    assembleDebugProject()
  }

  @Test
  fun addMultipleDynamicFeatureModulesToKtsBaseModule() {
    loadInitialProject()
    val project = projectRule.project

    val baseModuleModel = NewAndroidModuleModel.fromExistingProject(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = DefaultProjectSyncInvoker(),
      formFactor = FormFactor.Mobile,
      category = Category.Activity
    )
    generateModuleFiles(project, baseModuleModel, "base", useGradleKts = true) // Base module is always kts for this test

    val baseModule = project.findModule("base")
    createDefaultDynamicFeatureModel(project, "feature1", baseModule, useGradleKts, emptyProjectSyncInvoker)
    createDefaultDynamicFeatureModel(project, "feature2", baseModule, useGradleKts, emptyProjectSyncInvoker)

    assembleDebugProject()
  }

  @Test
  fun addNewAndroidLibraryModule() {
    loadInitialProject()

    val project = projectRule.project
    val libModuleModel = NewAndroidModuleModel.fromExistingProject(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = emptyProjectSyncInvoker,
      formFactor = FormFactor.Mobile,
      category = Category.Activity,
      isLibrary = true
    )
    generateModuleFiles(project, libModuleModel, "mylibrary", useGradleKts) // Base module is always kts for this test

    assembleDebugProject()
  }

  private fun assembleDebugProject() {
    projectRule.invokeTasks("assembleDebug").apply {
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }
}

private fun createDefaultDynamicFeatureModel(project: Project, moduleName: String, baseModule: Module, useGradleKts: Boolean,
                                             projectSyncInvoker: ProjectSyncInvoker) {
  val model = DynamicFeatureModel(
    project = project, moduleParent = ":", projectSyncInvoker = projectSyncInvoker,
    isInstant = false, templateName = "Dynamic Feature", templateDescription = "Dynamic Feature description"
  )
  model.baseApplication.value = baseModule // Dynamic Feature base module
  generateModuleFiles(project, model, moduleName, useGradleKts)
}

private fun generateModuleFiles(project: Project, model: ModuleModel, moduleName: String, useGradleKts: Boolean) {
  model.androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(HIGHEST_KNOWN_STABLE_API)
  model.moduleName.set(moduleName)
  model.template.set(createDefaultModuleTemplate(project, moduleName))
  model.packageName.set("com.example")
  model.useGradleKts.set(useGradleKts)

  model.handleFinished() // Generate module files
}