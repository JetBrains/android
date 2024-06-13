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
package com.android.tools.idea.templates

import com.android.sdklib.SdkVersionInfo
import com.android.testutils.MockitoKt
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.lint.common.getModuleDir
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.withKotlin
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.utils.FileUtils
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class KotlinBomPlatformDependencyTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()
  private val module by lazy { projectRule.getModule("app") }
  private val mockModuleTemplateData = MockitoKt.mock<ModuleTemplateData>()
  private val myModule = "mymodule"

  private val renderingContext by lazy {
    RenderingContext(
      projectRule.project,
      module,
      "KotlinBomPlatformDependencyTest",
      mockModuleTemplateData,
      moduleRoot = module.getModuleDir(),
      dryRun = false,
      showErrors = true
    )
  }

  private val projectTemplateDataBuilder by lazy {
    ProjectTemplateDataBuilder(true).apply {
      agpVersion = AgpVersions.newProject
      androidXSupport = true
      setProjectDefaults(projectRule.project)
      language = Language.Kotlin
      topOut = projectRule.project.guessProjectDir()!!.toIoFile()
      debugKeyStoreSha1 = KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore())
      applicationPackage = "com.example"
      overridePathCheck = true // To disable android plugin checking for ascii in paths (windows tests)
    }
  }

  private val moduleModel by lazy {
    NewAndroidModuleModel.fromExistingProject(
      project = projectRule.project,
      moduleParent = ":",
      projectSyncInvoker = emptyProjectSyncInvoker,
      formFactor = FormFactor.Mobile,
      category = Category.Activity,
      isLibrary = false
    )
  }

  private val recipeExecutor by lazy {
    DefaultRecipeExecutor(renderingContext)
  }

  // Ignore project sync (to speed up test), if later we are going to perform a gradle build anyway.
  private val emptyProjectSyncInvoker = object: ProjectSyncInvoker {
    override fun syncProject(project: Project) { }
  }

  @Before
  fun setUp() {
    MockitoKt.whenever(mockModuleTemplateData.projectTemplateData).thenReturn(projectTemplateDataBuilder.build())
  }

  @Test
  fun kgp1_7_20_hasTransitiveDepTo_kotlinStdLib1_8_0() {
    projectRule.load(TestProjectPaths.KOTLIN_WITH_VERSION_CATALOG, AGP_CURRENT.withKotlin("1.7.20"))

    generateModuleFiles(projectRule.project, moduleModel)
    val moduleDir = FileUtils.join(projectRule.project.basePath, myModule)
    // Adds a dependency that has a transitive dependency to kotlin-stdlib:1.8.0
    // to intentionally reproduce duplicated class without kotlin-bom as in
    // https://kotlinlang.org/docs/whatsnew18.html#bumping-the-minimum-supported-versions
    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1", moduleDir = File(moduleDir))
    runWriteCommandAction(projectRule.project) { recipeExecutor.applyChanges() }

    assembleDebugProject()
  }

  @Test
  fun kgp1_8_10_hasTransitiveDepToKotlinStdLib1_8_0() {
    projectRule.load(TestProjectPaths.KOTLIN_WITH_VERSION_CATALOG, AGP_CURRENT.withKotlin("1.8.10"))

    generateModuleFiles(projectRule.project, moduleModel)
    val moduleDir = FileUtils.join(projectRule.project.basePath, myModule)
    // Adds a dependency that has a transitive dependency to kotlin-stdlib:1.8.0
    recipeExecutor.addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1", moduleDir = File(moduleDir))
    runWriteCommandAction(projectRule.project) { recipeExecutor.applyChanges() }

    // kotlin-bom isn't added because kotlinVersion is > 1.8. We still verify the app compiles
    assembleDebugProject()
  }

  private fun assembleDebugProject() {
    projectRule.invokeTasks("assembleDebug").apply {
      buildError?.printStackTrace()
      Assert.assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }

  private fun generateModuleFiles(project: Project, model: ModuleModel) {
    model.androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
    model.moduleName.set(myModule)
    model.template.set(GradleAndroidModuleTemplate.createDefaultModuleTemplate(project, myModule))
    model.packageName.set("com.example")
    model.useGradleKts.set(true)

    model.handleFinished() // Generate module files
  }
}