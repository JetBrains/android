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
import com.android.tools.idea.npw.module.recipes.thingsModule.generateThingsModule
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.project.setGradleWrapperExecutable
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardParameterData
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
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
import com.android.tools.idea.wizard.template.Template as Template2

data class ProjectChecker(
  private val syncProject: Boolean,
  private val template: Template2,
  private val usageTracker: TestUsageTracker
) {
  private lateinit var moduleState: ModuleTemplateDataBuilder
  fun checkProject(moduleName: String, vararg customizers: ProjectStateCustomizer) {
    val modifiedModuleName = getModifiedModuleName(moduleName)
    val fixture = setUpFixtureForProject(modifiedModuleName)
    val project = fixture.project!!
    moduleState = getDefaultModuleState(project)
    customizers.forEach {
      it(moduleState, moduleState.projectTemplateDataBuilder)
    }

    try {
      createProject(fixture, modifiedModuleName)
      // TODO(qumeric): ProjectTemplateData[Builder] shuld use only one language [class]
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
    AndroidGradleTests.setUpSdks(fixture, getSdk())
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
      assertEquals(projectRoot, getBaseDirPath(this))
      AndroidGradleTests.importProject(this, Request.testRequest())
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
        com.android.tools.idea.npw.platform.Language.fromName(language.toString(), com.android.tools.idea.npw.platform.Language.JAVA),
        true, false
      )
    }

    val moduleRecipe: Recipe = when (template.formFactor) {
      // TODO(qumeric): support C++
      // TODO(qumeric): investigate why it requires 1.8 and does not work with 1.7
      FormFactor.Mobile -> { data: TemplateData -> this.generateAndroidModule(data as ModuleTemplateData, appTitle, false, false, "", "1.8") }
      FormFactor.Wear -> { data: TemplateData -> this.generateWearModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Tv -> { data: TemplateData -> this.generateTvModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Automotive -> { data: TemplateData -> this.generateAutomotiveModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Things -> { data: TemplateData -> this.generateThingsModule(data as ModuleTemplateData, appTitle, false) }
      FormFactor.Generic -> { data: TemplateData -> this.generatePureLibrary(data as ModuleTemplateData, "LibraryTemplate") }
    }

    val projectContext = RenderingContext2(
      project = this,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleState.projectTemplateDataBuilder.build(),
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )

    val context = RenderingContext2(
      project = this,
      module = null,
      commandName = "Run TemplateTest",
      templateData = moduleState.build(),
      moduleRoot = moduleRoot,
      dryRun = false,
      showErrors = true
    )

    // TODO(qumeric): why doesn't it work with one executor?
    val executor1 = DefaultRecipeExecutor2(context)
    val executor2 = DefaultRecipeExecutor2(context)
    val executor3 = DefaultRecipeExecutor2(context)

    WizardParameterData(moduleState.packageName!!, false, "main", template.parameters)
    (template.parameters.find { it.name == "Package name" } as StringParameter?)?.value = moduleState.packageName!!

    projectRecipe.render(projectContext, executor1, null)
    moduleRecipe.render(context, executor2, null)
    template.render(context, executor3)
    setGradleWrapperExecutable(projectRoot)
    verifyLastLoggedUsage(usageTracker, titleToTemplateRenderer(template.name), moduleState.build())

    // Make sure we didn't forgot to specify a thumbnail
    assertNotEquals(template.thumb(), Thumb.NoThumb)
    // Make sure project root is set up correctly
    assertEquals(projectRoot, guessProjectDir()!!.toIoFile())
  }
}


@VisibleForTesting
fun titleToTemplateRenderer(title: String?) = when (title) {
  "" -> AndroidStudioEvent.TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER
  Template.ANDROID_MODULE_TEMPLATE -> AndroidStudioEvent.TemplateRenderer.ANDROID_MODULE
  Template.ANDROID_PROJECT_TEMPLATE -> AndroidStudioEvent.TemplateRenderer.ANDROID_PROJECT
  "Empty Activity" -> AndroidStudioEvent.TemplateRenderer.EMPTY_ACTIVITY
  "Blank Activity" -> AndroidStudioEvent.TemplateRenderer.BLANK_ACTIVITY
  "Layout XML File" -> AndroidStudioEvent.TemplateRenderer.LAYOUT_XML_FILE
  "Fragment (Blank)" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_BLANK
  "Navigation Drawer Activity" -> AndroidStudioEvent.TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY
  "Values XML File" -> AndroidStudioEvent.TemplateRenderer.VALUES_XML_FILE
  "Google Maps Activity" -> AndroidStudioEvent.TemplateRenderer.GOOGLE_MAPS_ACTIVITY
  "Login Activity" -> AndroidStudioEvent.TemplateRenderer.LOGIN_ACTIVITY
  "Assets Folder" -> AndroidStudioEvent.TemplateRenderer.ASSETS_FOLDER
  "Tabbed Activity" -> AndroidStudioEvent.TemplateRenderer.TABBED_ACTIVITY
  "Scrolling Activity" -> AndroidStudioEvent.TemplateRenderer.SCROLLING_ACTIVITY
  "Fullscreen Activity" -> AndroidStudioEvent.TemplateRenderer.FULLSCREEN_ACTIVITY
  "Service" -> AndroidStudioEvent.TemplateRenderer.SERVICE
  "Java Library" -> AndroidStudioEvent.TemplateRenderer.JAVA_LIBRARY
  "Settings Activity" -> AndroidStudioEvent.TemplateRenderer.SETTINGS_ACTIVITY
  "Fragment (List)" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_LIST
  "Master/Detail Flow" -> AndroidStudioEvent.TemplateRenderer.MASTER_DETAIL_FLOW
  "Wear OS Module" -> AndroidStudioEvent.TemplateRenderer.ANDROID_WEAR_MODULE
  "Broadcast Receiver" -> AndroidStudioEvent.TemplateRenderer.BROADCAST_RECEIVER
  "AIDL File" -> AndroidStudioEvent.TemplateRenderer.AIDL_FILE
  "Service (IntentService)" -> AndroidStudioEvent.TemplateRenderer.INTENT_SERVICE
  "JNI Folder" -> AndroidStudioEvent.TemplateRenderer.JNI_FOLDER
  "Java Folder" -> AndroidStudioEvent.TemplateRenderer.JAVA_FOLDER
  "Custom View" -> AndroidStudioEvent.TemplateRenderer.CUSTOM_VIEW
  "Android TV Module" -> AndroidStudioEvent.TemplateRenderer.ANDROID_TV_MODULE
  "Google AdMob Ads Activity" -> AndroidStudioEvent.TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY
  "Always On Wear Activity" -> AndroidStudioEvent.TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY
  "Res Folder" -> AndroidStudioEvent.TemplateRenderer.RES_FOLDER
  "Android TV Activity" -> AndroidStudioEvent.TemplateRenderer.ANDROID_TV_ACTIVITY
  "Basic Activity" -> AndroidStudioEvent.TemplateRenderer.BASIC_ACTIVITIY
  "App Widget" -> AndroidStudioEvent.TemplateRenderer.APP_WIDGET
  "Instant App Project" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_PROJECT
  "Instant App" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_MODULE
  "Dynamic Feature (Instant App)" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
  "Benchmark Module" -> AndroidStudioEvent.TemplateRenderer.BENCHMARK_LIBRARY_MODULE
  "Fullscreen Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_FULLSCREEN
  "Google AdMob Ads Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_GOOGLE_ADMOB_ADS
  "Google Maps Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_GOOGLE_MAPS
  "Login Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_LOGIN
  "Modal Bottom Sheet" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_MODAL_BOTTOM_SHEET
  "Scrolling Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_SCROLL
  "Settings Fragment" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_SETTINGS
  "Fragment (with ViewModel)" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_VIEWMODEL
  "Empty Compose Activity" -> AndroidStudioEvent.TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  else -> AndroidStudioEvent.TemplateRenderer.CUSTOM_TEMPLATE_RENDERER
}
