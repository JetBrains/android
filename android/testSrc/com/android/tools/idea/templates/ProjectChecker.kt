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
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.testutils.TestUtils.getSdk
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker.Request
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.setGradleWrapperExecutable
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.Template.titleToTemplateRenderer
import com.android.tools.idea.templates.TemplateAttributes.ATTR_AIDL_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_TOOLS_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_PLUGIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_KOTLIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MANIFEST_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_RES_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SDK_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SRC_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TARGET_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TARGET_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TEST_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_THEME_EXISTS
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TOP_OUT
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.WizardParameterData
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.junit.Assert.assertEquals
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException
import com.android.tools.idea.wizard.template.Template as Template2

data class ProjectChecker(
  private val syncProject: Boolean,
  private val projectState: TestNewProjectWizardState,
  private val activityState: TestTemplateWizardState,
  private val usageTracker: TestUsageTracker,
  private val language: Language,
  private val createActivity: Boolean
) {
  private val moduleState: TestTemplateWizardState get() = projectState.moduleTemplateState

  fun checkProject(projectName: String) {
    if (activityState[COMPARE_NEW_RENDERING_CONTEXT] != null && activityState.getBoolean(COMPARE_NEW_RENDERING_CONTEXT)) {
      return compareProject(projectName)
    }
    val modifiedProjectName = getModifiedProjectName(projectName, activityState)
    val fixture = setUpFixtureForProject(modifiedProjectName)
    val project = fixture.project!!
    try {
      createProject(fixture, modifiedProjectName)
      project.verify(language)
    }
    finally {
      fixture.tearDown()
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
      cleanupProjectFiles(getBaseDirPath(project))
    }
  }

  private fun createProject(fixture: JavaCodeInsightTestFixture, projectName: String, isNewRenderingContext: Boolean = false) {
    val project = fixture.project!!
    IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java))
    AndroidGradleTests.setUpSdks(fixture, getSdk())
    val projectDir = getBaseDirPath(project)
    moduleState.put(ATTR_MODULE_NAME, projectName)
    moduleState.put(ATTR_TOP_OUT, projectDir.path)
    println("Checking project $projectName in $projectDir")
    project.createWithIconGenerator()
    val projectRoot = project.guessProjectDir()!!.toIoFile()
    if (!createActivity) {
      val template = activityState.template
      val moduleRoot = File(projectRoot, projectName)
      activityState.apply {
        put(ATTR_TOP_OUT, projectDir.path)
        put(ATTR_MODULE_NAME, moduleRoot.name)
        put(ATTR_SOURCE_PROVIDER_NAME, "main")
        populateDirectoryParameters()
      }
      if (isNewRenderingContext) {
        val newTemplates = TemplateResolver.EP_NAME.extensions.flatMap { it.getTemplates() }
        val projectNameBase = projectName.split("_").getOrElse(0) { projectName }
        val newTemplate = newTemplates.find { it.name.replace(" ", "") == projectNameBase }!!
        createProjectForNewRenderingContext(project, activityState, newTemplate)
      }
      else {
        val context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.templateValues)
        ApplicationManager.getApplication().runWriteAction {
          template.render(context, false)
          addIconsIfNecessary(activityState)
        }
      }
    }
  }

  private fun Project.verify(language: Language) {
    val projectDir = getBaseDirPath(this)
    verifyLanguageFiles(projectDir, language)
    invokeGradleForProjectDir(projectDir)
    lintIfNeeded(this)
  }

  /**
   * Compare the contents of the generated files between the old and new RenderingContexts
   */
  private fun compareProject(projectName: String) {
    val modifiedProjectName = getModifiedProjectName(projectName, activityState, true)
    val fixtureForOld = setUpFixtureForProject(modifiedProjectName)
    val oldProject = fixtureForOld.project!!
    val oldTempDirFixture = TempDirTestFixtureImpl().apply {
      setUp()
    }
    try {
      createProject(fixtureForOld, modifiedProjectName)
      FileUtil.copyFileOrDir(FilePaths.toSystemDependentPath(oldProject.basePath)!!, File(oldTempDirFixture.tempDirPath))
    }
    finally {
      fixtureForOld.tearDown()
    }
    val fixtureForNew = setUpFixtureForProject(modifiedProjectName)
    val newProject = fixtureForNew.project!!
    val newBaseDir = getBaseDirPath(newProject)
    val newTempDirFixture = TempDirTestFixtureImpl().apply {
      setUp()
    }
    try {
      createProject(fixtureForNew, modifiedProjectName, true)
      FileUtil.copyFileOrDir(newBaseDir, File(newTempDirFixture.tempDirPath))
      oldTempDirFixture.tempDirPath compareFilesBetweenNewAndOldRenderingContexts newTempDirFixture.tempDirPath
      // Verify the created project after comparison so that intermediate files are excluded from the comparison
      newProject.verify(language)
    }
    finally {
      fixtureForNew.tearDown()
      oldTempDirFixture.tearDown()
      newTempDirFixture.tearDown()
      cleanupProjectFiles(getBaseDirPath(oldProject))
      cleanupProjectFiles(newBaseDir)
      cleanupProjectFiles(File(oldTempDirFixture.tempDirPath))
      cleanupProjectFiles(File(newTempDirFixture.tempDirPath))
    }
  }

  private infix fun String.compareFilesBetweenNewAndOldRenderingContexts(projectBaseNew: String) {
    // File names except for the baseDirectory for the old RenderingContext
    val oldGeneratedFiles = File(this).walk().filter { it.isFile }.map {
      val fileName = it.absolutePath.split(this + File.separatorChar)[1]
      if (!comparisonExcludedPaths.contains(fileName)) {
        // Assert the contents of each file between the new and old RenderingContext
        val expected = FileUtil.loadFile(it).trimAllWhitespace()
        val actual = FileUtil.loadFile(File(projectBaseNew + File.separatorChar + fileName)).trimAllWhitespace()
        assertEquals(expected, actual)
      }
      fileName
    }.toSet()

    // File names except for the baseDirectory for the new RenderingContext
    val newGeneratedFiles = File(projectBaseNew).walk().filter { it.isFile }.map {
      it.absolutePath.split(projectBaseNew + File.separatorChar)[1]
    }.toSet()

    // Assert the generated set of files are equivalent
    assertEquals(oldGeneratedFiles, newGeneratedFiles)
  }

  private fun createProjectForNewRenderingContext(
    project: Project, activityState: TestTemplateWizardState, newTemplate: Template2
  ) {
    val packageName = activityState.getString(ATTR_PACKAGE_NAME)
    val generateLayout = activityState["generateLayout"] as Boolean?
    val projectRoot = VfsUtilCore.virtualToIoFile(project.guessProjectDir()!!)
    val modifiedProjectName = getModifiedProjectName(project.name, activityState, true)
    val moduleRoot = File(projectRoot, modifiedProjectName)
    val projectTemplateDataBuilder = ProjectTemplateDataBuilder(!createActivity).apply {
      minApi = activityState.getString(ATTR_MIN_API)
      minApiLevel = activityState.getInt(ATTR_MIN_API_LEVEL)
      buildApi = activityState.getInt(ATTR_BUILD_API)
      androidXSupport = activityState.getBoolean(ATTR_ANDROIDX_SUPPORT)
      buildApiString = activityState.getString(ATTR_BUILD_API_STRING)
      buildApiRevision = 0
      targetApi = activityState.getInt(ATTR_TARGET_API)
      targetApiString = activityState.getString(ATTR_TARGET_API_STRING)
      gradlePluginVersion = GradleVersion.tryParse(activityState.getString(ATTR_GRADLE_PLUGIN_VERSION))
      javaVersion = JavaVersion.parse("1.8")
      sdkDir = File(activityState.getString(ATTR_SDK_DIR))
      language = Language.fromName(activityState.getString(ATTR_LANGUAGE), Language.KOTLIN)
      kotlinVersion = activityState.getString(ATTR_KOTLIN_VERSION)
      buildToolsVersion = Revision.parseRevision(activityState.getString(ATTR_BUILD_TOOLS_VERSION))
      topOut = moduleRoot
      applicationPackage = null
    }
    val moduleTemplateData = ModuleTemplateData(
      projectTemplateDataBuilder.build(),
      File(activityState.getString(ATTR_SRC_OUT)),
      File(activityState.getString(ATTR_RES_OUT)),
      File(activityState.getString(ATTR_MANIFEST_OUT)),
      File(activityState.getString(ATTR_TEST_OUT)),
      File(activityState.getString(ATTR_AIDL_OUT)),
      File(activityState.getString(ATTR_TOP_OUT)),
      activityState.getBoolean(ATTR_THEME_EXISTS),
      activityState.getBoolean(ATTR_IS_NEW_MODULE),
      activityState.getBoolean(ATTR_HAS_APPLICATION_THEME),
      moduleState.getString(ATTR_MODULE_NAME),
      moduleState.getBoolean(ATTR_IS_LIBRARY_MODULE),
      packageName,
      FormFactor.Mobile,
      ThemesData(),
      null
    )
    val context = RenderingContext2(
      project = project,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleTemplateData,
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )
    val executor = DefaultRecipeExecutor2(context)
    // Updates wizardParameterData for all parameters.
    WizardParameterData(packageName, false, "main", newTemplate.parameters)
    (newTemplate.parameters.find { it.name == "Package name" } as StringParameter?)?.value = packageName
    (newTemplate.parameters.find { it.name == "Generate a Layout File" } as BooleanParameter?)?.value = generateLayout!!
    runWriteAction {
      newTemplate.render(context, executor)
    }
  }


  // TODO(parentej) test the icon generator
  private fun Project.createWithIconGenerator() {
    runWriteAction {
      val minSdkVersion = moduleState.getString(ATTR_MIN_API).toInt()
      val iconGenerator = LauncherIconGenerator(this, minSdkVersion, null)
      try {
        iconGenerator.outputName().set("ic_launcher")
        iconGenerator.sourceAsset().value = ImageAsset()
        this.create(iconGenerator)
      }
      finally {
        Disposer.dispose(iconGenerator)
      }
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    val projectRoot = File(moduleState.getString(ATTR_TOP_OUT))
    assertEquals(projectRoot, guessProjectDir()!!.toIoFile())
    updateGradleAndSyncIfNeeded(projectRoot, moduleState.getString(ATTR_MODULE_NAME))
  }

  private fun Project.updateGradleAndSyncIfNeeded(projectRoot: File, moduleName: String) {
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION)
    val gradleFile = File(projectRoot, SdkConstants.FN_BUILD_GRADLE)
    val origContent = Files.asCharSource(gradleFile, UTF_8).read()
    val newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy())
    if (newContent != origContent) {
      Files.asCharSink(gradleFile, UTF_8).write(newContent)
    }
    refreshProjectFiles()
    if (syncProject) {
      assertEquals(moduleName, name)
      assertEquals(projectRoot, getBaseDirPath(this))
      AndroidGradleTests.importProject(this, Request.testRequest())
    }
  }

  /**
   * Renders the project.
   */
  private fun Project.create(iconGenerator: IconGenerator?) {
    moduleState.populateDirectoryParameters()
    val projectPath = moduleState.getString(ATTR_TOP_OUT)
    val moduleName = moduleState.getString(ATTR_MODULE_NAME)
    val paths = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).paths

    val projectRoot = File(projectPath)
    val moduleRoot = paths.moduleRoot!!

    val projectTemplate = projectState.projectTemplate
    val moduleTemplate = moduleState.template

    projectState.updateParameters()

    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '$projectPath'.")
    }

    fun createRenderingContext(template: Template, templateValues: Map<String, Any>) =
      createRenderingContext(template, this, projectRoot, moduleRoot, templateValues)

    fun Template.renderAndCheck(templateValues: Map<String, Any>): MutableCollection<File> {
      val context = createRenderingContext(this, templateValues)
      render(context, false)
      usageTracker.checkAfterRender(metadata!!, templateValues)
      return context.filesToOpen
    }

    // TODO(qumeric): should it be projectState.templateValues?
    projectTemplate.renderAndCheck(moduleState.templateValues)
    setGradleWrapperExecutable(projectRoot)
    val moduleFilesToOpen = moduleTemplate.renderAndCheck(moduleState.templateValues)

    if (createActivity) {
      val activityState = projectState.activityTemplateState
      val activityFilesToOpen = activityState.template.renderAndCheck(activityState.templateValues)
      moduleFilesToOpen.addAll(activityFilesToOpen)
    }
  }

  private fun TestUsageTracker.checkAfterRender(metadata: TemplateMetadata, paramMap: Map<String, Any>) =
    verifyLastLoggedUsage(this, titleToTemplateRenderer(metadata.title), paramMap)

  companion object {
    /**
     * Set of relative file paths that are going to be excluded from the comparison of
     * [compareFilesBetweenNewAndOldRenderingContexts].
     */
    private val comparisonExcludedPaths = setOf(
      "gradle/wrapper/gradle-wrapper.properties" // Created time should be different
    )

    // It is fine to do comparison ignoring whitespace because we do format all files after rendering anyway.
    private fun String.trimAllWhitespace() = this.split("\n")
      .filter(String::isNotBlank)
      .joinToString("\n", transform = String::trim)
  }
}