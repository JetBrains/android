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
import com.android.tools.idea.npw.project.setGradleWrapperExecutable
import com.android.tools.idea.templates.Template.titleToTemplateRenderer
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TOP_OUT
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy
import com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.util.toIoFile
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.junit.Assert.assertEquals
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

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
    val modifiedProjectName = getModifiedProjectName(projectName, activityState)
    val fixture = setUpFixtureForProject(modifiedProjectName)
    val project = fixture.project!!
    IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java))
    AndroidGradleTests.setUpSdks(fixture, getSdk())
    val projectDir = getBaseDirPath(project)
    moduleState.put(ATTR_MODULE_NAME, modifiedProjectName)
    moduleState.put(ATTR_TOP_OUT, projectDir.path)
    println("Checking project $projectName in $projectDir")
    try {
      project.createWithIconGenerator()
      val projectRoot = project.guessProjectDir()!!.toIoFile()
      if (!createActivity) {
        val template = activityState.template
        val moduleRoot = File(projectRoot, modifiedProjectName)
        activityState.apply {
          put(ATTR_TOP_OUT, projectDir.path)
          put(ATTR_MODULE_NAME, moduleRoot.name)
          put(ATTR_SOURCE_PROVIDER_NAME, "main")
          populateDirectoryParameters()
        }
        val context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.templateValues)
        ApplicationManager.getApplication().runWriteAction {
          template.render(context, false)
          addIconsIfNecessary(activityState)
        }
      }
      verifyLanguageFiles(projectDir, language)
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
}