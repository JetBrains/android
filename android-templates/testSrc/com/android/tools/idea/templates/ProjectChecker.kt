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
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.benchmarkModule.generateBenchmarkModule
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.module.recipes.thingsModule.generateThingsModule
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.setGradleWrapperExecutable
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore
import com.android.tools.idea.templates.Template.titleToTemplateRenderer
import com.android.tools.idea.templates.TemplateAttributes.ATTR_AIDL_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_TOOLS_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_CLASS_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_CPP_SUPPORT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_PLUGIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_KOTLIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MANIFEST_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_OVERRIDE_PATH_CHECK
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PROJECT_OUT
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
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardParameterData
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException
import com.android.tools.idea.wizard.template.Template as Template2

enum class ActivityCreationMode {
  WITH_PROJECT,
  WITHOUT_PROJECT, // first we will create a project and module and then an activity
  DO_NOT_CREATE
}

data class ProjectChecker(
  private val syncProject: Boolean,
  private val projectState: TestNewProjectWizardState,
  private val activityState: TestTemplateWizardState,
  private val usageTracker: TestUsageTracker,
  private val language: Language,
  private val activityCreationMode: ActivityCreationMode
) {
  private val moduleState: TestTemplateWizardState get() = projectState.moduleTemplateState

  /**
   * TODO(qumeric): add documentation
   */
  fun checkProject(moduleName: String) {
    if (activityState[COMPARE_NEW_RENDERING_CONTEXT] != null && activityState.getBoolean(COMPARE_NEW_RENDERING_CONTEXT)) {
      return compareProject(moduleName)
    }
    val modifiedModuleName = getModifiedModuleName(moduleName)
    val fixture = setUpFixtureForProject(modifiedModuleName)
    val project = fixture.project!!
    try {
      createProject(fixture, modifiedModuleName)
      project.verify(language)
    }
    finally {
      fixture.tearDown()
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
      cleanupProjectFiles(getBaseDirPath(project))
    }
  }

  private fun createProject(fixture: JavaCodeInsightTestFixture, moduleName: String, isNewRenderingContext: Boolean = false) {
    val project = fixture.project!!
    IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java))
    AndroidGradleTests.setUpSdks(fixture, getSdk())
    val projectRoot = project.guessProjectDir()!!.toIoFile()
    moduleState.put(ATTR_TOP_OUT, projectRoot.path)
    println("Checking project $moduleName in $projectRoot")
    project.create(moduleName, isNewRenderingContext)
    project.updateGradleAndSyncIfNeeded(projectRoot, moduleName)
  }

  private fun Project.verify(language: Language) {
    val projectDir = getBaseDirPath(this)
    verifyLanguageFiles(projectDir, language)
    if(basePath?.contains("compare") != true) {
      invokeGradleForProjectDir(projectDir)
    }
    lintIfNeeded(this)
  }

  /**
   * Compare the contents of the generated files between the old and new RenderingContexts
   */
  private fun compareProject(moduleName: String) {
    val modifiedModuleName = getModifiedModuleName(moduleName)
    val fixtureForOld = setUpFixtureForProject(modifiedModuleName)
    val oldProject = fixtureForOld.project!!
    val oldTempDirFixture = TempDirTestFixtureImpl().apply {
      setUp()
    }
    try {
      createProject(fixtureForOld, modifiedModuleName)
      FileUtil.copyFileOrDir(FilePaths.toSystemDependentPath(oldProject.basePath)!!, File(oldTempDirFixture.tempDirPath))
    }
    finally {
      fixtureForOld.tearDown()
    }
    val fixtureForNew = setUpFixtureForProject(modifiedModuleName)
    val newProject = fixtureForNew.project!!
    val newBaseDir = getBaseDirPath(newProject)
    val newTempDirFixture = TempDirTestFixtureImpl().apply {
      setUp()
    }
    try {
      createProject(fixtureForNew, modifiedModuleName, true)
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
      // substring(1) removes the first separatorChar
      val relativePath = it.path.removePrefix(this).substring(1)
      if (!comparisonExcludedPaths.contains(relativePath.replace(File.separatorChar, '/'))) {
        /** Forces templates to conform to the same style */
        fun String.harmonize(): String {
          val mainHarmonization = this
            .replace('\'', '"')
            .replace(" : ", ": ")
            .replace(" (", "(")
            .trimAllWhitespace()

          // For complicated reasons, we have a different line order for the new modules. Therefore, we only check if lines are the same.
          return mainHarmonization.takeUnless { "Module" in projectBaseNew } ?: mainHarmonization.split("\n").sorted().joinToString("\n")
        }
        // Compare the contents of each file between the new and the old RenderingContext
        val expected = FileUtil.loadFile(it).harmonize()
        val actual = FileUtil.loadFile(File(projectBaseNew + File.separatorChar + relativePath)).harmonize()
        assertEquals("Contents of $relativePath are different", expected, actual)
      }
      relativePath
    }.toSet()

    // File names except for the baseDirectory for the new RenderingContext
    val newGeneratedFiles = File(projectBaseNew).walk().filter { it.isFile }.map {
      it.path.removePrefix(projectBaseNew).substring(1)
    }.toSet()

    // Assert the generated set of files are equivalent
    assertEquals(oldGeneratedFiles, newGeneratedFiles)
  }

  private fun createProjectForNewRenderingContext(
    project: Project, moduleName: String, activityState: TestTemplateWizardState, newTemplate: Template2? = null, recipe: Recipe? = null
  ) {
    requireNotNull(newTemplate ?: recipe) { "Either a template or a recipe should be passed" }
    require(newTemplate == null || recipe == null) { "Either a template or a recipe should be passed, not both" }
    val isLauncher = activityState.getBoolean(ATTR_IS_LAUNCHER)
    val packageName = activityState.getString(ATTR_PACKAGE_NAME)
    val generateLayout = activityState["generateLayout"] as Boolean?
    val projectRoot = VfsUtilCore.virtualToIoFile(project.guessProjectDir()!!)
    val moduleRoot = File(projectRoot, moduleName)
    val projectTemplateDataBuilder = ProjectTemplateDataBuilder(activityCreationMode == ActivityCreationMode.WITHOUT_PROJECT).apply {
      androidXSupport = activityState.getBoolean(ATTR_ANDROIDX_SUPPORT)
      gradlePluginVersion = GradleVersion.tryParse(activityState.getString(ATTR_GRADLE_PLUGIN_VERSION))
      javaVersion = JavaVersion.parse("1.8")
      sdkDir = File(activityState.getString(ATTR_SDK_DIR))
      language = Language.fromName(activityState.getString(ATTR_LANGUAGE), Language.KOTLIN)
      kotlinVersion = activityState.getString(ATTR_KOTLIN_VERSION)
      buildToolsVersion = Revision.parseRevision(activityState.getString(ATTR_BUILD_TOOLS_VERSION))
      topOut = File(activityState.getString(ATTR_TOP_OUT))
      applicationPackage = null
      debugKeyStoreSha1 = KeystoreUtils.sha1(getOrCreateDefaultDebugKeystore())
      overridePathCheck = activityState.getBoolean(ATTR_OVERRIDE_PATH_CHECK)
    }
    val apis = ApiTemplateData(
      minApi = activityState.getString(ATTR_MIN_API),
      minApiLevel = activityState.getInt(ATTR_MIN_API_LEVEL),
      buildApi = activityState.getInt(ATTR_BUILD_API),
      buildApiString = activityState.getString(ATTR_BUILD_API_STRING),
      buildApiRevision = 0,
      targetApi = activityState.getInt(ATTR_TARGET_API),
      targetApiString = activityState.getString(ATTR_TARGET_API_STRING)
    )

    val moduleTemplateData = ModuleTemplateData(
      projectTemplateDataBuilder.build(),
      File(activityState.getString(ATTR_SRC_OUT)),
      File(activityState.getString(ATTR_RES_OUT)),
      File(activityState.getString(ATTR_MANIFEST_OUT)),
      File(activityState.getString(ATTR_TEST_OUT)),
      File(activityState.getString(ATTR_TEST_OUT).replace("androidTest", "test")), // this is unavailable in the old system
      File(activityState.getString(ATTR_AIDL_OUT)),
      File(activityState.getString(ATTR_PROJECT_OUT)),
      activityState.getBoolean(ATTR_THEME_EXISTS),
      activityState.getBoolean(ATTR_IS_NEW_MODULE),
      activityState.getBoolean(ATTR_HAS_APPLICATION_THEME),
      moduleState.getString(ATTR_MODULE_NAME),
      moduleState.getBoolean(ATTR_IS_LIBRARY_MODULE),
      packageName,
      FormFactor.Mobile,
      ThemesData(),
      null,
      apis
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
    if (newTemplate != null) {
      WizardParameterData(packageName, false, "main", newTemplate.parameters)
      (newTemplate.parameters.find { it.name == "Package name" } as StringParameter?)?.value = packageName
      (newTemplate.parameters.find { it.name == "Generate a Layout File" } as BooleanParameter?)?.value = generateLayout!!
      (newTemplate.parameters.find { it.name == "Launcher Activity" } as BooleanParameter?)?.value = isLauncher!!
      // TODO: More generalized way of overriding the parameters
      val overrideBooleanParameters = listOf(
        "multipleScreens" to "Split settings hierarchy into separate sub-screens",
        "isThingsLauncher" to "Launch activity automatically on boot"
      )
      overrideBooleanParameters.forEach {(id, name) ->
        activityState[id]?.let { value ->
          (newTemplate.parameters.find { it.name == name } as BooleanParameter?)?.value =
            value as Boolean
        }
      }
      runWriteAction {
        newTemplate.render(context, executor)
      }
    } else {
      recipe!!.render(context, executor, null)
    }
  }

  private fun Project.updateGradleAndSyncIfNeeded(projectRoot: File, moduleName: String) {
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION)
    val gradleFile = File(projectRoot, SdkConstants.FN_BUILD_GRADLE)
    val origContent = Files.asCharSource(gradleFile, UTF_8).read()
    val newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy())
    if (newContent != origContent) {
      Files.asCharSink(gradleFile, UTF_8).write(newContent)
    }
    // Bug 146077926
    val gradleDocument = FileDocumentManager.getInstance().getDocument(gradleFile.toVirtualFile()!!)!!
    FileDocumentManager.getInstance().reloadFromDisk(gradleDocument)
    refreshProjectFiles()
    if (syncProject) {
      assertEquals(moduleName, name)
      assertEquals(projectRoot, getBaseDirPath(this))
      AndroidGradleTests.importProject(this, Request.testRequest())
    }
  }

  /**
   * Renders project, module and possibly activity template. Also checks if logging was correct after each rendering step.
   */
  private fun Project.create(moduleName: String, isNewRenderingContext: Boolean) = runWriteAction {
    val projectPath = moduleState.getString(ATTR_TOP_OUT)
    val projectRoot = File(projectPath)
    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '$projectPath'.")
    }

    moduleState.put(ATTR_MODULE_NAME, moduleName)

    moduleState.populateDirectoryParameters()
    val moduleRoot = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).paths.moduleRoot!!

    projectState.updateParameters()

    fun Template.renderAndCheck(templateValues: Map<String, Any>) {
      val context = createRenderingContext(this, this@create, projectRoot, moduleRoot, templateValues)
      render(context, false)
      verifyLastLoggedUsage(usageTracker, titleToTemplateRenderer(metadata!!.title, metadata!!.formFactor), templateValues)
    }

    // TODO(qumeric): should it be projectState.templateValues?
    projectState.projectTemplate.renderAndCheck(moduleState.templateValues)
    setGradleWrapperExecutable(projectRoot)

    // if mode is "DO_NOT_CREATE" then "activityState" actually contains module state
    if (activityCreationMode != ActivityCreationMode.DO_NOT_CREATE) {
      if (isNewRenderingContext) {
        val appTitle = moduleState.getString(ATTR_APP_TITLE)
        // Template is not needed to render the module, obtaining the Template just to check the FormFactor
        val template = activityState.template
        val newTemplates = TemplateResolver.EP_NAME.extensions.flatMap { it.getTemplates() }
        val newTemplate = newTemplates.find { it.name == template.metadata?.title }!!
        val recipe: Recipe = when (newTemplate.formFactor) {
          FormFactor.Mobile -> { data: TemplateData -> this.generateAndroidModule(data as ModuleTemplateData, appTitle, false, "") }
          FormFactor.Wear -> { data: TemplateData -> this.generateWearModule(data as ModuleTemplateData, appTitle) }
          FormFactor.Tv -> { data: TemplateData -> this.generateTvModule(data as ModuleTemplateData, appTitle) }
          FormFactor.Automotive -> { data: TemplateData -> this.generateAutomotiveModule(data as ModuleTemplateData, appTitle) }
          FormFactor.Things -> { data: TemplateData -> this.generateThingsModule(data as ModuleTemplateData, appTitle) }
          FormFactor.Generic -> { data: TemplateData -> this.generatePureLibrary(data as ModuleTemplateData, moduleState.getString(ATTR_CLASS_NAME)) }
        }
        createProjectForNewRenderingContext(this, moduleName, activityState, recipe = recipe)
      }
      else {
        moduleState.template.renderAndCheck(moduleState.templateValues)
      }
    }

    when (activityCreationMode) {
      ActivityCreationMode.WITH_PROJECT -> {
        val activityState = projectState.activityTemplateState
        activityState.template.renderAndCheck(activityState.templateValues)
      }
      ActivityCreationMode.WITHOUT_PROJECT -> {
        val template = activityState.template
        activityState.apply {
          put(ATTR_TOP_OUT, projectPath)
          put(ATTR_MODULE_NAME, moduleName)
          put(ATTR_SOURCE_PROVIDER_NAME, "main")
          populateDirectoryParameters()
        }
        if (isNewRenderingContext) {
          val newTemplates = TemplateResolver.EP_NAME.extensions.flatMap { it.getTemplates() }
          val newTemplate = newTemplates.find { it.name == template.metadata?.title }!!

          // Make sure we didn't forgot to specify a thumbnail
          assertNotEquals(newTemplate.thumb(), Thumb.NoThumb)

          createProjectForNewRenderingContext(this, moduleName, activityState, newTemplate)
        }
        else {
          template.renderAndCheck(activityState.templateValues)
        }
      }
      ActivityCreationMode.DO_NOT_CREATE -> {
        if (isNewRenderingContext) {
          val appTitle = moduleState[ATTR_APP_TITLE] as String?
          val className = moduleState[ATTR_CLASS_NAME] as String?
          val cppSupport = moduleState[ATTR_CPP_SUPPORT] as Boolean? ?: false
          val recipe: Recipe = { data: TemplateData ->
            when {
              // TODO(qumeric) pass cppFlags?
              moduleName.contains("NewAndroidModule") -> this.generateAndroidModule(data as ModuleTemplateData, appTitle!!, cppSupport, "")
              moduleName.contains("NewAndroidAutomotiveModule") -> this.generateAutomotiveModule(data as ModuleTemplateData, appTitle!!)
              moduleName.contains("NewAndroidThingsModule") -> this.generateThingsModule(data as ModuleTemplateData, appTitle!!)
              moduleName.contains("NewAndroidTVModule") -> this.generateTvModule(data as ModuleTemplateData, appTitle!!)
              moduleName.contains("AndroidWearModule") -> this.generateWearModule(data as ModuleTemplateData, appTitle!!)
              moduleName.contains("NewJavaOrKotlinLibrary") -> this.generatePureLibrary(data as ModuleTemplateData, className!!)
              moduleName.contains("NewBenchmarkModule") -> this.generateBenchmarkModule(data as ModuleTemplateData)
              else -> throw IllegalArgumentException("given module name ($moduleName) is unknown")
            }
          }
          createProjectForNewRenderingContext(this, moduleName, activityState, recipe = recipe)
        } else {
          activityState.template.renderAndCheck(activityState.templateValues)
        }
      }
    }

    assertEquals(projectRoot, guessProjectDir()!!.toIoFile())
  }

  companion object {
    /**
     * Set of relative file paths that are going to be excluded from the comparison of [compareFilesBetweenNewAndOldRenderingContexts].
     */
    private val comparisonExcludedPaths = setOf(
      "gradle/wrapper/gradle-wrapper.properties" // Creation time may be different
    )

    // It is fine to do comparison ignoring whitespace because we do format all files after rendering anyway.
    private fun String.trimAllWhitespace() = this.split("\n")
      .filter(String::isNotBlank)
      .joinToString("\n", transform = String::trim)
  }
}