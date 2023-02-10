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
package com.android.tools.idea.templates.diff

import com.android.SdkConstants
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.ProjectStateCustomizer
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getTestDataRoot
import com.android.tools.idea.templates.getDefaultModuleState
import com.android.tools.idea.templates.getModifiedModuleName
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.WizardParameterData
import com.android.utils.FileUtils
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.IOException
import java.nio.file.Path

// We ignore these directories because they just contain metadata uninteresting to templates, and it saves space for golden files
val FILES_TO_IGNORE = arrayOf(".gradle", ".idea", "local.properties")

abstract class ProjectRenderer(private val template: Template) {
  private lateinit var moduleState: ModuleTemplateDataBuilder

  fun renderProject(projectRule: AndroidGradleProjectRule,
                    moduleName: String,
                    avoidModifiedModuleName: Boolean = false,
                    vararg customizers: ProjectStateCustomizer) {
    val project = projectRule.project
    val modifiedModuleName = getModifiedModuleName(moduleName, avoidModifiedModuleName)
    moduleState = getDefaultModuleState(project, template)
    customizers.forEach {
      it(moduleState, moduleState.projectTemplateDataBuilder)
    }

    try {
      createProject(projectRule, modifiedModuleName)
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
      FormFactor.Mobile -> { data: TemplateData ->
        this.generateAndroidModule(
          data as ModuleTemplateData, appTitle, false, BytecodeLevel.L8)
      }

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

    // Without the AGP version specified, it uses the current version, which could change often
    projectRule.load(TestProjectPaths.NO_MODULES, getPinnedAgpVersion()) {
      runWriteActionAndWait {
        writeDefaultTomlFile(projectRule.project, moduleRecipeExecutor)
        moduleRecipe.render(context, moduleRecipeExecutor)
        // Executor for the template needs to apply changes so that the toml file is visible in the executor
        templateRecipeExecutor.applyChanges()
        template.render(context, templateRecipeExecutor)
      }
    }

    // TODO: generify this to use probably the unmodified module name as the golden directory name
    val goldenDir = getTestDataRoot().resolve("golden").resolve("testNewEmptyViewActivity")
    handleDirectories(goldenDir, projectRoot.toPath())
  }

  abstract fun handleDirectories(goldenDir: Path, projectDir: Path)
}

private fun writeDefaultTomlFile(project: Project, executor: DefaultRecipeExecutor) {
  WriteCommandAction.writeCommandAction(project).run<IOException> {
    executor.copy(
      File(FileUtils.join("fileTemplates", "internal", "Version Catalog File.versions.toml.ft")),
      File(project.basePath, FileUtils.join("gradle", SdkConstants.FN_VERSION_CATALOG)))
    executor.applyChanges()
  }
}