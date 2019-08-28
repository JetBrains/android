/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.generator

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.NPW_GENERATED_PROJECTS_DIR
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.getProjectCacheDir
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.*
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.Template.CATEGORY_PROJECTS
import com.android.tools.idea.templates.TemplateMetadata.*
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.wizard.WizardConstants.MODULE_TEMPLATE_NAME
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.ThreadTracker
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.sdk.AndroidSdkData
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

// New project is created only in first iteration of first test. Later only modules/activities are created
private var firstRun = true

// TODO(qumeric): add "no activity" and "watch face" support

private val mobileActivities = listOf(
  "BasicActivity",
  "EmptyActivity",
  "BottomNavigationActivity",
  "FullscreenActivity",
  "MasterDetailFlow",
  "NavigationDrawerActivity"
)

private val wearActivities = listOf(
  "BlankWearActivity",
  "GoogleMapsWearActivity"
)

/**
 * This generates NPW projects from "popular" sets of input combinations (e.g. mobile project with min API 21 and BasicActivity)
 * Actually not a test but a tool. See b/111785663
 */
class GenerateShippedSyncTest : AndroidTestBase() {
  // Generate projects only for the following (most popular/latest) version of sdks.
  private val interestingMinApiLevels: Set<Int> = setOf(17, 19, 21, 23, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
  private val interestingTargetApiLevels: Set<Int> = setOf(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)

  private lateinit var sdkData: AndroidSdkData

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    // This is necessary when we don't create a default project,
    // to ensure that the LocalFileSystemHolder is initialized.
    IdeaTestApplication.getInstance()

    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib")

    // Replace the default RepositoryUrlManager with one that enables repository checks in tests. (myForceRepositoryChecksInTests)
    // This is necessary to fully resolve dynamic gradle coordinates such as ...:appcompat-v7:+ => appcompat-v7:25.3.1
    // keeping it exactly the same as they are resolved within the NPW flow.
    IdeComponents(null) {}.replaceApplicationService(
      RepositoryUrlManager::class.java,
      RepositoryUrlManager(IdeGoogleMavenRepository, true))

    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true)

    ensureSdkManagerAvailable()
    sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()!!
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride()
  }

  private fun generateProjects(projectName: String, activities: List<String>) {
    val templateRootFolder = TemplateManager.getTemplateRootFolder()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createFixtureBuilder(projectName)
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
    myFixture!!.setUp()
    myFixture.project.replaceService(PostProjectBuildTasksExecutor::class.java, mock(PostProjectBuildTasksExecutor::class.java), testRootDisposable)

    val projectDir: File = Projects.getBaseDirPath(myFixture!!.project)

    for (activity in activities) {
      val templateFile = File(templateRootFolder, "activities" + File.separator + activity)
      executeActivityTemplate(templateFile, projectDir.path)
    }

    createGradleWrapper(projectDir)
    Files.move(projectDir.toPath(), Paths.get(NPW_GENERATED_PROJECTS_DIR))
  }

  @Throws(Exception::class)
  fun ignore_testCachePhoneAndTabletActivities() = generateProjects("phone_and_tablet_activities", mobileActivities)

  @Throws(Exception::class)
  fun ignore_testCacheWearActivities() = generateProjects("wear_activities", wearActivities)

  private fun executeActivityTemplate(templateFile: File, projectLocation: String) {
    val projectState = TestNewProjectWizardState(getModuleTemplateForFormFactor(templateFile)).apply {
      activityTemplateState.setTemplateLocation(templateFile)
    }

    val moduleState = projectState.moduleTemplateState.apply {
      Template.convertApisToInt(parameters)
      TemplateValueInjector(parameters).addGradleVersions(null)
      put(ATTR_IS_INSTANT_APP, false)
      put(ATTR_PROJECT_LOCATION, projectLocation)
      put(ATTR_BUILD_TOOLS_VERSION, sdkData.getLatestBuildTool(false)!!.revision.toString())
    }

    val interestingTargets = sdkData.targets.filter { interestingTargetApiLevels.contains(it.version.apiLevel) }

    for (target in interestingTargets) {
      val targetSdk = target.version.apiLevel

      // TODO(qumeric) workaround. Decide if we should support it and add support if yes
      if (target.version.codename == "P") continue

      for (minSdk in interestingMinApiLevels) {
        if (minSdk > targetSdk) {
          continue
        }
        val cacheDir = getProjectCacheDir(templateFile.name, minSdk, targetSdk)
        moduleState.put(ATTR_MODULE_NAME, cacheDir)
        moduleState.populateDirectoryParameters()
        projectState.updateParameters()
        System.out.printf("Create %s, min %d, target %d\n", templateFile.path, minSdk, targetSdk)
        setAttrsAndCreateProject(minSdk, target, projectState)
      }
    }
  }

  private fun setAttrsAndCreateProject(minSdk: Int, target: IAndroidTarget, projectState: TestNewProjectWizardState) {
    val targetSdk = target.version.apiLevel

    with(projectState.moduleTemplateState) {
      put(ATTR_MIN_API, Integer.toString(minSdk))
      put(ATTR_MIN_API_LEVEL, minSdk)
      put(ATTR_TARGET_API, targetSdk)
      put(ATTR_TARGET_API_STRING, Integer.toString(targetSdk))
      put(ATTR_BUILD_API, targetSdk)
      put(ATTR_BUILD_API_STRING, getBuildApiString(target.version))
    }

    ApplicationManager.getApplication().runWriteAction {
      createProject(projectState, myFixture!!.project)
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }
}

private fun createProject(projectState: TestNewProjectWizardState, project: Project) {
  val moduleState = projectState.moduleTemplateState
  moduleState.populateDirectoryParameters()
  val moduleName = moduleState.getString(ATTR_MODULE_NAME)
  val projectRoot = File(moduleState.getString(ATTR_PROJECT_LOCATION)!!)
  val moduleRoot = File(projectRoot, moduleName!!)
  if (FileUtilRt.createDirectory(projectRoot)) {
    projectState.updateParameters()

    if (firstRun) {
      val projectTemplate = projectState.projectTemplate
      val projectContext = createRenderingContext(projectTemplate, project, projectRoot, moduleRoot, moduleState.parameters)
      projectTemplate.render(projectContext, false)
      AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot)
      firstRun = false
    }

    val moduleContext = createRenderingContext(moduleState.template, project, projectRoot, moduleRoot, moduleState.parameters)
    val moduleTemplate = moduleState.template
    moduleTemplate.render(moduleContext, false)

    val activityTemplateState = projectState.activityTemplateState
    val activityTemplate = activityTemplateState.template
    val activityContext = createRenderingContext(activityTemplate, project, projectRoot, moduleRoot, activityTemplateState.parameters)
    activityTemplate.render(activityContext, false)
  }
}

private fun createRenderingContext(projectTemplate: Template,
                                   project: Project,
                                   projectRoot: File,
                                   moduleRoot: File,
                                   parameters: Map<String, Any>?): RenderingContext {
  return RenderingContext.Builder.newContext(projectTemplate, project)
    .withOutputRoot(projectRoot)
    .withModuleRoot(moduleRoot)
    .also {
      parameters?.run { it.withParams(parameters) }
    }.build()
}

private fun getModuleTemplateForFormFactor(templateFile: File): Template {
  val activityTemplate = Template.createFromPath(templateFile)
  val moduleTemplate = Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME)
  val activityMetadata = activityTemplate.metadata
  val activityFormFactorName = activityMetadata!!.formFactor ?: return moduleTemplate
  val activityFormFactor = FormFactor.get(activityFormFactorName)
  if (activityFormFactor != FormFactor.CAR) {
    val manager = TemplateManager.getInstance()
    val applicationTemplates = manager.getTemplatesInCategory(CATEGORY_APPLICATION)
    for (formFactorTemplateFile in applicationTemplates) {
      val metadata = manager.getTemplateMetadata(formFactorTemplateFile)
      if (metadata?.formFactor != null && FormFactor.get(metadata.formFactor!!) == activityFormFactor) {
        return Template.createFromPath(formFactorTemplateFile)
      }
    }
  }
  return moduleTemplate
}

// This was basically copied from AndroidGradleTestCase to remove inheritance from it.
fun createGradleWrapper(projectRoot: File) {
  val wrapper = GradleWrapper.create(projectRoot)
  val path = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(GRADLE_LATEST_VERSION)!!
  wrapper.updateDistributionUrl(path)
}
