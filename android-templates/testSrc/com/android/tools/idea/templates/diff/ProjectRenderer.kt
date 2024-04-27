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
import com.android.test.testutils.TestUtils
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getTestDataRoot
import com.android.tools.idea.templates.getDefaultModuleState
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.prepareGradleProject
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
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.IOException
import java.nio.file.Path

// We ignore these directories because they just contain metadata uninteresting to templates, and it
// saves space for golden files
val FILES_TO_IGNORE = emptyArray<String>()
// val FILES_TO_IGNORE = arrayOf(".gradle", ".idea", "local.properties")

abstract class ProjectRenderer(protected val template: Template, val goldenDirName: String) {
  protected lateinit var moduleState: ModuleTemplateDataBuilder

  fun renderProject(project: Project, vararg customizers: ProjectStateCustomizer) {
    moduleState = getDefaultModuleState(project, template)
    customizers.forEach { it(moduleState, moduleState.projectTemplateDataBuilder) }

    try {
      createProject(project)
    } finally {
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
    }
  }

  /** Renders project, module and possibly activity template. */
  private fun createProject(project: Project) {
    val moduleName = moduleState.name!!

    val projectRoot = moduleState.projectTemplateDataBuilder.topOut!!
    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '$projectRoot'.")
    }

    println("Creating project $moduleName in $projectRoot")

    val moduleRoot =
      GradleAndroidModuleTemplate.createDefaultTemplateAt(File(projectRoot.path, moduleName))
        .paths
        .moduleRoot!!

    val appTitle = "Template Test App Title"

    val moduleRecipe: Recipe =
      when (template.formFactor) {
        // TODO(qumeric): support C++
        // TODO(qumeric): investigate why it requires 1.8 and does not work with 1.7
        FormFactor.Mobile -> { data: TemplateData ->
            this.generateAndroidModule(
              data as ModuleTemplateData,
              appTitle,
              false,
              BytecodeLevel.L8
            )
          }
        FormFactor.Wear -> { data: TemplateData ->
            this.generateWearModule(data as ModuleTemplateData, appTitle, false)
          }
        FormFactor.Tv -> { data: TemplateData ->
            this.generateTvModule(data as ModuleTemplateData, appTitle, false)
          }
        FormFactor.Automotive -> { data: TemplateData ->
            this.generateAutomotiveModule(data as ModuleTemplateData, appTitle, false)
          }
        FormFactor.Generic -> { data: TemplateData ->
            this.generatePureLibrary(data as ModuleTemplateData, "LibraryTemplate", false)
          }
      }

    val context =
      RenderingContext(
        project = project,
        module = null,
        commandName = "Run TemplateTest",
        templateData = moduleState.build(),
        moduleRoot = moduleRoot,
        dryRun = false,
        showErrors = true
      )

    println("Using template ${template.name}")

    // TODO(qumeric): why doesn't it work with one executor?
    val moduleRecipeExecutor = DefaultRecipeExecutor(context)
    val templateRecipeExecutor = DefaultRecipeExecutor(context)

    WizardParameterData(moduleState.packageName!!, false, "main", template.parameters)
    (template.parameters.find { it.name == "Package name" } as StringParameter?)?.value =
      moduleState.packageName!!

    prepareProject(projectRoot)
    renderTemplate(project, moduleRecipe, context, moduleRecipeExecutor, templateRecipeExecutor)

    // TODO: make sure it's unique even with different params
    val goldenDir = getTestDataRoot().resolve("golden").resolve(goldenDirName)
    handleDirectories(goldenDirName, goldenDir, projectRoot.toPath())
  }

  protected open fun prepareProject(projectRoot: File) {}

  /**
   * Copies in build.gradle, gradle.properties, and settings.gradle from
   * testData/projects/projectWithNoModules
   *
   * When using AndroidGradleProjectRule, this is unnecessary because it's already done by load()
   */
  protected fun prepareProjectImpl(projectRoot: File) {
    prepareGradleProject(getNoModulesTestProjectPath(), projectRoot) {
      // Normally, a "patcher" function here would generate the gradlew and gradlew.bat files and
      // fill out build.gradle, but since the templates don't affect those files, we can skip them
      // in the diff and make our golden snapshots smaller.
    }
  }

  protected open fun renderTemplate(
    project: Project,
    moduleRecipe: Recipe,
    context: RenderingContext,
    moduleRecipeExecutor: DefaultRecipeExecutor,
    templateRecipeExecutor: DefaultRecipeExecutor
  ) {
    runWriteActionAndWait {
      writeDefaultTomlFile(project, moduleRecipeExecutor)
      moduleRecipe.render(context, moduleRecipeExecutor)
      // Executor for the template needs to apply changes so that the toml file is visible in the
      // executor
      templateRecipeExecutor.applyChanges()
      template.render(context, templateRecipeExecutor)
    }
  }

  /**
   * To be overridden to handle the golden and template output directories after the template
   * project is generated
   *
   * @param moduleName a unique name for this template, used to determine the directory name to
   *   output golden files to
   * @param goldenDir the location of this template's golden reference files
   * @param projectDir the location of this template-generated project
   */
  abstract fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path)
}

internal fun writeDefaultTomlFile(project: Project, executor: DefaultRecipeExecutor) {
  WriteCommandAction.writeCommandAction(project).run<IOException> {
    executor.copy(
      File(FileUtils.join("fileTemplates", "internal", "Version_Catalog_File.versions.toml.ft")),
      File(project.basePath, FileUtils.join("gradle", SdkConstants.FN_VERSION_CATALOG))
    )
    executor.applyChanges()
  }
}

private fun getNoModulesTestProjectPath(): File {
  return TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData")
    .resolve(TestProjectPaths.NO_MODULES)
    .toFile()
}
