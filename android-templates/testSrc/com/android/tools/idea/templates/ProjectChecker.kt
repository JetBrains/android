/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.injectBuildOutputDumpingBuildViewManager
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardParameterData
import com.android.utils.FileUtils
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtilRt
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import java.io.File
import java.io.IOException

data class ProjectChecker(
  private val syncProject: Boolean,
  private val template: Template,
  private val usageTracker: TestUsageTracker
) {
  private lateinit var moduleState: ModuleTemplateDataBuilder

  fun checkProject(projectRule: AndroidGradleProjectRule, moduleName: String, avoidModifiedModuleName: Boolean = false, vararg customizers: ProjectStateCustomizer) {
    val project = projectRule.project
    val modifiedModuleName = getModifiedModuleName(moduleName, avoidModifiedModuleName)
    moduleState = getDefaultModuleState(project, template)
    customizers.forEach {
      it(moduleState, moduleState.projectTemplateDataBuilder)
    }

    try {
      createProject(projectRule, modifiedModuleName)
      // TODO(b/149006038): ProjectTemplateData[Builder] should use only one language [class]
      val language = Language.valueOf(moduleState.projectTemplateDataBuilder.language!!.toString())
      project.verify(projectRule, language)
    }
    finally {
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
    }
  }

  private fun createProject(projectRule: AndroidGradleProjectRule, moduleName: String) {
    val projectRoot = projectRule.project.guessProjectDir()!!.toIoFile()
    println("Checking project $moduleName in $projectRoot")
    createProject(projectRule)
  }

  private fun Project.verify(projectRule: AndroidGradleProjectRule, language: Language) {
    val projectDir = getBaseDirPath(this)
    verifyLanguageFiles(projectDir, language)
    if (basePath?.contains("Folder") != true) { // running Gradle for new folders doesn't make much sense and takes long time
      injectBuildOutputDumpingBuildViewManager(projectRule.project, projectRule.project)
      projectRule.invokeTasks("compileDebugSources").apply { // "assembleDebug" is too slow
        buildError?.printStackTrace()
        Assert.assertTrue("Project didn't compile correctly", isBuildSuccessful)
      }
    }
    lintIfNeeded(this)
    checkDslParser(this)
  }

  /**
   * Renders project, module and possibly activity template. Also checks if logging was correct after each rendering step.
   */
  private fun createProject(projectRule: AndroidGradleProjectRule) {
    val moduleName = moduleState.name!!

    val projectRoot = moduleState.projectTemplateDataBuilder.topOut!!
    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '$projectRoot'.")
    }

    val moduleRoot = GradleAndroidModuleTemplate.createDefaultTemplateAt(File(projectRoot.path, moduleName)).paths.moduleRoot!!

    val appTitle = "Template Test App Title"

    val moduleRecipe: Recipe = when (template.formFactor) {
      // TODO(qumeric): support C++
      // TODO(qumeric): investigate why it requires 1.8 and does not work with 1.7
      FormFactor.Mobile -> { data: TemplateData -> this.generateAndroidModule(
        data as ModuleTemplateData, appTitle, false, BytecodeLevel.L8) }
      FormFactor.Wear -> { data: TemplateData -> this.generateWearModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Tv -> { data: TemplateData -> this.generateTvModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Automotive -> { data: TemplateData -> this.generateAutomotiveModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Generic -> { data: TemplateData -> this.generatePureLibrary(data as ModuleTemplateData, "LibraryTemplate", false) }
    }

    val context = RenderingContext(
      project = projectRule.project,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleState.build(),
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )

    // TODO(qumeric): why doesn't it work with one executor?
    val moduleRecipeExecutor = DefaultRecipeExecutor(context)
    val templateRecipeExecutor = DefaultRecipeExecutor(context)

    WizardParameterData(moduleState.packageName!!, false, "main", template.parameters)
    (template.parameters.find { it.name == "Package name" } as StringParameter?)?.value = moduleState.packageName!!

    projectRule.load(TestProjectPaths.NO_MODULES) {
      runWriteActionAndWait {
        writeDefaultTomlFile(projectRule.project, moduleRecipeExecutor)
        moduleRecipe.render(context, moduleRecipeExecutor)
        // Executor for the template needs to apply changes so that the toml file is visible in the executor
        templateRecipeExecutor.applyChanges()
        template.render(context, templateRecipeExecutor)
      }
    }
    verifyLastLoggedUsage(usageTracker, template.name, template.formFactor, moduleState.build())

    // Make sure we didn't forget to specify a thumbnail
    assertNotEquals(template.thumb(), Thumb.NoThumb)
    // Make sure project root is set up correctly
    assertEquals(projectRoot, projectRule.project.guessProjectDir()!!.toIoFile())
  }

  private fun writeDefaultTomlFile(project: Project, executor: DefaultRecipeExecutor) {
    WriteCommandAction.writeCommandAction(project).run<IOException> {
      executor.copy(
        File(FileUtils.join("fileTemplates", "internal", "Version Catalog File.versions.toml.ft")),
        File(project.basePath, FileUtils.join("gradle", SdkConstants.FN_VERSION_CATALOG)))
      executor.applyChanges()
    }
  }
}
