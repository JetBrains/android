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
import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.testutils.TestUtils.getSdk
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker.Request
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.androidProject.androidProjectRecipe
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.project.setGradleWrapperExecutable
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardParameterData
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

data class ProjectChecker(
  private val syncProject: Boolean,
  private val template: Template,
  private val usageTracker: TestUsageTracker
) {
  private lateinit var moduleState: ModuleTemplateDataBuilder

  fun checkProject(moduleName: String, avoidModifiedModuleName: Boolean = false, vararg customizers: ProjectStateCustomizer) {
    val modifiedModuleName = getModifiedModuleName(moduleName, avoidModifiedModuleName)
    val fixture = setUpFixtureForProject(modifiedModuleName)
    val project = fixture.project!!
    moduleState = getDefaultModuleState(project)
    customizers.forEach {
      it(moduleState, moduleState.projectTemplateDataBuilder)
    }

    try {
      createProject(fixture, modifiedModuleName)
      // TODO(b/149006038): ProjectTemplateData[Builder] should use only one language [class]
      val language = Language.valueOf(moduleState.projectTemplateDataBuilder.language!!.toString())
      project.verify(language)
    }
    finally {
      fixture.tearDown()
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
      cleanupProjectFiles(getBaseDirPath(project))
    }
  }

  private fun createProject(fixture: JavaCodeInsightTestFixture, moduleName: String) {
    val project = fixture.project!!
    IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java))
    AndroidGradleTests.setUpSdks(fixture, getSdk().toFile())
    val projectRoot = project.guessProjectDir()!!.toIoFile()
    println("Checking project $moduleName in $projectRoot")
    project.create()
    project.updateGradleAndSyncIfNeeded(projectRoot)
  }

  private fun Project.verify(language: Language) {
    val projectDir = getBaseDirPath(this)
    verifyLanguageFiles(projectDir, language)
    if (basePath?.contains("Folder") != true) { // running Gradle for new folders doesn't make much sense and takes long time
      invokeGradleForProjectDir(projectDir)
    }
    lintIfNeeded(this)
  }

  private fun Project.updateGradleAndSyncIfNeeded(projectRoot: File) {
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION)
    val gradleFile = File(projectRoot, SdkConstants.FN_BUILD_GRADLE)
    val origContent = gradleFile.readText()
    val newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy())
    gradleFile.writeText(origContent, newContent)

    val settingsFile = File(projectRoot, SdkConstants.FN_SETTINGS_GRADLE)
    val settingsOrigContent = settingsFile.readText()
    val settingsNewContent = updateLocalRepositories(settingsOrigContent, getLocalRepositoriesForGroovy())
    settingsFile.writeText(settingsOrigContent, settingsNewContent)

    refreshProjectFiles()
    if (syncProject) {
      assertEquals(projectRoot, getBaseDirPath(this))
      AndroidGradleTests.importProject(this, Request.testRequest(), null)
    }
  }

  private fun File.readText(): String {
    val fileDocument = FileDocumentManager.getInstance().getDocument(this.toVirtualFile()!!)!!
    return fileDocument.text
  }

  private fun File.writeText(origContent: String, newContent: String) {
    if (newContent != origContent) {
      WriteAction.runAndWait<RuntimeException> {
        val fileDocument = FileDocumentManager.getInstance().getDocument(this.toVirtualFile()!!)!!
        fileDocument.setText(newContent)
        FileDocumentManager.getInstance().saveDocument(fileDocument)
      }
    }
  }

  /**
   * Renders project, module and possibly activity template. Also checks if logging was correct after each rendering step.
   */
  private fun Project.create() = runWriteAction {
    val moduleName = moduleState.name!!

    val projectRoot = moduleState.projectTemplateDataBuilder.topOut!!
    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '$projectRoot'.")
    }

    val moduleRoot = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectRoot.path, moduleName).paths.moduleRoot!!

    val appTitle = "Template Test App Title"

    val language = moduleState.projectTemplateDataBuilder.language
    val projectRecipe: Recipe = { data: TemplateData ->
      androidProjectRecipe(
        data as ProjectTemplateData, "Template Test project",
        language!!, true, false, forceNonTransitiveRClass = true
      )
    }

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

    val projectContext = RenderingContext(
      project = this,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleState.projectTemplateDataBuilder.build(),
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )

    val context = RenderingContext(
      project = this,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleState.build(),
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )

    // TODO(qumeric): why doesn't it work with one executor?
    val executor1 = DefaultRecipeExecutor(context)
    val executor2 = DefaultRecipeExecutor(context)
    val executor3 = DefaultRecipeExecutor(context)

    WizardParameterData(moduleState.packageName!!, false, "main", template.parameters)
    (template.parameters.find { it.name == "Package name" } as StringParameter?)?.value = moduleState.packageName!!

    projectRecipe.render(projectContext, executor1)
    moduleRecipe.render(context, executor2)
    template.render(context, executor3)
    setGradleWrapperExecutable(projectRoot)
    verifyLastLoggedUsage(usageTracker, template.name, template.formFactor, moduleState.build())

    // Make sure we didn't forgot to specify a thumbnail
    assertNotEquals(template.thumb(), Thumb.NoThumb)
    // Make sure project root is set up correctly
    assertEquals(projectRoot, guessProjectDir()!!.toIoFile())
  }
}
