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
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils
import com.android.tools.idea.templates.Template.titleToTemplateRenderer
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT
import com.android.tools.idea.templates.TemplateTestBase.Companion.ATTR_CREATE_ACTIVITY
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories
import com.android.tools.idea.testing.IdeComponents
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.junit.Assert.assertEquals
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

data class ProjectChecker(
  private val syncProject: Boolean,
  private val projectState: TestNewProjectWizardState,
  private val activityState: TestTemplateWizardState?,
  private val usageTracker: TestUsageTracker,
  private val language: Language
) {
  @Throws(Exception::class)
  fun checkProjectNow(projectName: String) {
    val moduleState = projectState.moduleTemplateState
    val modifiedProjectName = getModifiedProjectName(projectName, activityState)
    moduleState.put(ATTR_MODULE_NAME, modifiedProjectName)
    val fixture = setUpFixtureForProject(modifiedProjectName)
    val project = fixture.project!!
    IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java))
    AndroidGradleTests.setUpSdks(fixture, getSdk())
    val projectDir = getBaseDirPath(project)
    moduleState.put(ATTR_TOP_OUT, projectDir.path)
    println("Checking project $projectName in ${project.guessProjectDir()}")
    try {
      createProject(fixture)
      val projectRoot = virtualToIoFile(project.guessProjectDir()!!)
      if (activityState != null && !moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
        val template = activityState.template
        val moduleRoot = File(projectRoot, modifiedProjectName)
        activityState.apply {
          put(ATTR_TOP_OUT, projectDir.path)
          put(ATTR_MODULE_NAME, moduleRoot.name)
          put(ATTR_SOURCE_PROVIDER_NAME, "main")
          populateDirectoryParameters()
        }
        val context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.parameters)
        ApplicationManager.getApplication().runWriteAction {
          template.render(context, false)
          addIconsIfNecessary(activityState)
        }
      }
      if (language === Language.KOTLIN) {
        verifyLanguageFiles(projectDir, Language.KOTLIN)
      }
      invokeGradleForProjectDir(projectDir)
      lintIfNeeded(project)
    }
    finally {
      fixture.tearDown()
      val openProjects = ProjectManagerEx.getInstanceEx().openProjects
      assert(openProjects.size <= 1) // 1: the project created by default by the test case
      cleanupProjectFiles(projectDir)
    }
  }

  @Throws(Exception::class)
  private fun createProject(fixture: JavaCodeInsightTestFixture) {
    val moduleState = projectState.moduleTemplateState
    ApplicationManager.getApplication().runWriteAction {
      val minSdkVersion = moduleState.get(ATTR_MIN_API).toString().toInt()
      val iconGenerator = LauncherIconGenerator(fixture.project, minSdkVersion, null)
      try {
        iconGenerator.outputName().set("ic_launcher")
        iconGenerator.sourceAsset().value = ImageAsset()
        createProject(fixture.project, iconGenerator)
      }
      finally {
        Disposer.dispose(iconGenerator)
      }
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Update to latest plugin / gradle and sync model
    val projectRoot = File(moduleState.getString(ATTR_TOP_OUT)!!)
    assertEquals(projectRoot, virtualToIoFile(fixture.project.guessProjectDir()!!))
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION)
    val gradleFile = File(projectRoot, SdkConstants.FN_BUILD_GRADLE)
    val origContent = Files.asCharSource(gradleFile, UTF_8).read()
    val newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy())
    if (newContent != origContent) {
      Files.asCharSink(gradleFile, UTF_8).write(newContent)
    }
    val project = fixture.project
    refreshProjectFiles()
    if (syncProject) {
      assertEquals(moduleState.getString(ATTR_MODULE_NAME), project.name)
      assertEquals(projectRoot, getBaseDirPath(project))
      AndroidGradleTests.importProject(project, Request.testRequest())
    }
  }

  private fun createProject(project: Project, iconGenerator: IconGenerator?) {
    val moduleState = projectState.moduleTemplateState.also {
      it.populateDirectoryParameters()
    }
    val moduleName = moduleState.getString(ATTR_MODULE_NAME)!!
    val projectPath = moduleState.getString(ATTR_TOP_OUT)!!
    val projectRoot = File(projectPath)
    val paths = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).paths

    if (!FileUtilRt.createDirectory(projectRoot)) {
      throw IOException("Unable to create directory '%$projectPath'.")
    }

    if (iconGenerator != null) {
      // TODO(parentej) test the icon generator
    }

    projectState.updateParameters()
    val moduleRoot = paths.moduleRoot!!
    // If this is a new project, instantiate the project-level files
    val projectTemplate = projectState.projectTemplate
    val projectContext = createRenderingContext(projectTemplate, project, projectRoot, moduleRoot, moduleState.parameters)
    projectTemplate.render(projectContext, false)

    // check usage tracker after project render
    verifyLastLoggedUsage(usageTracker, titleToTemplateRenderer(projectTemplate.metadata!!.title), projectContext.paramMap)
    AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot)
    val moduleContext = createRenderingContext(moduleState.template, project, projectRoot, moduleRoot, moduleState.parameters)
    val moduleTemplate = moduleState.template
    moduleTemplate.render(moduleContext, false)

    // check usage tracker after module render
    verifyLastLoggedUsage(usageTracker, titleToTemplateRenderer(moduleTemplate.metadata!!.title), moduleContext.paramMap)
    if (moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
      val activityTemplateState = projectState.activityTemplateState
      val activityTemplate = activityTemplateState.template
      val activityContext = createRenderingContext(activityTemplate, project, moduleRoot, moduleRoot, activityTemplateState.parameters)
      activityTemplate.render(activityContext, false)

      // check usage tracker after activity render
      verifyLastLoggedUsage(usageTracker, titleToTemplateRenderer(activityTemplate.metadata!!.title), activityContext.paramMap)
      moduleContext.filesToOpen.addAll(activityContext.filesToOpen)
    }
  }
}