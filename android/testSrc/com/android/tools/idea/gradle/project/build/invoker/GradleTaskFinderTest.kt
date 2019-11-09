/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeVariant
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.testing.IdeComponents
import com.google.common.collect.Sets
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.testFramework.PlatformTestCase
import junit.framework.TestCase
import org.gradle.tooling.model.GradleProject
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.nio.file.Paths

/**
 * Tests for [GradleTaskFinder].
 */
class GradleTaskFinderTest : PlatformTestCase() {
  @Mock
  private lateinit var androidModel: AndroidModuleModel
  @Mock
  private lateinit var ideAndroidProject: IdeAndroidProject
  @Mock
  private lateinit var ideVariant: IdeVariant
  @Mock
  private lateinit var mainArtifact: IdeAndroidArtifact
  @Mock
  private lateinit var artifact: IdeBaseArtifact
  @Mock
  private lateinit var testCompileType: TestCompileType
  @Mock
  private lateinit var rootPathFinder: GradleRootPathFinder
  @Mock
  private lateinit var androidModel2: AndroidModuleModel
  @Mock
  private lateinit var ideAndroidProject2: IdeAndroidProject
  private lateinit var modules: Array<Module>
  private lateinit var taskFinder: GradleTaskFinder

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)
    val project = project
    val projectRootPath = Projects.getBaseDirPath(project).path
    Mockito.`when`(rootPathFinder.getProjectRootPath(module)).thenReturn(Paths.get(projectRootPath))
    modules = arrayOf(module)
    taskFinder = GradleTaskFinder(rootPathFinder)
  }

  fun testCreateBuildTaskWithTopLevelModule() {
    val task = taskFinder.createBuildTask(":", "assemble")
    TestCase.assertEquals(":assemble", task)
  }

  fun testFindTasksToExecuteWhenLastSyncFailed() {
    val syncState = Mockito.mock(GradleSyncState::class.java)
    IdeComponents(project).replaceProjectService(GradleSyncState::class.java, syncState)
    Mockito.`when`(syncState.lastSyncFailed()).thenReturn(true)
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly("assemble")
  }

  fun testFindTasksWithBuildSrcModule() {
    val module = createModule("buildSrc")
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(arrayOf(module), BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).isEmpty()
  }

  fun testFindTasksWithNonGradleModule() {
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).isEmpty()
  }

  fun testFindTasksWithEmptyGradlePath() {
    Facets.createAndAddGradleFacet(module)
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).isEmpty()
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask1",
      ":testFindTasksToExecuteWhenCleaningAndroidProject:afterSyncTask2",
      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask1",
      ":testFindTasksToExecuteWhenCleaningAndroidProject:ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask1",
      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:afterSyncTask2",
      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask1",
      ":testFindTasksToExecuteForSourceGenerationInAndroidProject:ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask1",
      ":testFindTasksToExecuteForAssemblingAndroidProject:assembleTask2"
    )
  }

  fun testFindTasksToExecuteForRebuildingAndroidProject() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.REBUILD, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      "clean",
      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask1",
      ":testFindTasksToExecuteForRebuildingAndroidProject:assembleTask2"
    )
    // Make sure clean is the first task (b/78443416)
    Truth.assertThat(tasks[0]).isEqualTo("clean")
  }

  fun testFindTasksToExecuteForCompilingAndroidProject() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForCompilingAndroidProject:compileTask1",
      ":testFindTasksToExecuteForCompilingAndroidProject:compileTask2",
      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask1",
      ":testFindTasksToExecuteForCompilingAndroidProject:ideSetupTask2",
      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask1",
      ":testFindTasksToExecuteForCompilingAndroidProject:afterSyncTask2"
    )
  }

  fun testFindTasksToExecuteForCompilingDynamicApp() {
    setUpModuleAsAndroidModule()
    // Create and setup dynamic feature module
    val featureModule = createModule("feature1")
    setUpModuleAsAndroidModule(featureModule, androidModel2, ideAndroidProject2)
    Mockito.`when`(ideAndroidProject.dynamicFeatures).thenReturn(listOf(":feature1"))
    Mockito.`when`(ideAndroidProject2.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE)
    val projectRootPath = Projects.getBaseDirPath(project).path
    Mockito.`when`(rootPathFinder.getProjectRootPath(featureModule)).thenReturn(Paths.get(projectRootPath))
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForCompilingDynamicApp:assembleTask1",
      ":testFindTasksToExecuteForCompilingDynamicApp:assembleTask2",
      ":feature1:assembleTask1",
      ":feature1:assembleTask2"
    )
  }

  fun testFindTasksToExecuteForBundleTool() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":testFindTasksToExecuteForBundleTool:bundleTask1")
  }

  fun testFindTasksToExecuteForApkFromBundle() {
    setUpModuleAsAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":testFindTasksToExecuteForApkFromBundle:apkFromBundleTask1")
  }

  private fun setUpModuleAsAndroidModule() {
    setUpModuleAsAndroidModule(module, androidModel, ideAndroidProject)
  }

  private fun setUpModuleAsAndroidModule(
    module: Module,
    androidModel: AndroidModuleModel,
    ideAndroidProject: IdeAndroidProject
  ) {
    setUpModuleAsGradleModule(module)
    Mockito.`when`<IdeVariant?>(androidModel.selectedVariant).thenReturn(ideVariant)
    Mockito.`when`(testCompileType.getArtifacts(ideVariant)).thenReturn(setOf(artifact))
    Mockito.`when`<IdeAndroidProject?>(androidModel.androidProject).thenReturn(ideAndroidProject)
    val androidModelFeatures = Mockito.mock(AndroidModelFeatures::class.java)
    Mockito.`when`(androidModelFeatures.isTestedTargetVariantsSupported).thenReturn(false)
    Mockito.`when`(androidModel.features).thenReturn(androidModelFeatures)
    Mockito.`when`<IdeAndroidArtifact?>(ideVariant.mainArtifact).thenReturn(mainArtifact)
    Mockito.`when`(mainArtifact.bundleTaskName).thenReturn("bundleTask1")
    Mockito.`when`(mainArtifact.apkFromBundleTaskName).thenReturn("apkFromBundleTask1")
    Mockito.`when`(artifact.assembleTaskName).thenReturn("assembleTask1")
    Mockito.`when`(artifact.compileTaskName).thenReturn("compileTask1")
    Mockito.`when`(artifact.ideSetupTaskNames).thenReturn(Sets.newHashSet("ideSetupTask1", "ideSetupTask2"))
    val androidFacet = Facets.createAndAddAndroidFacet(module)
    val state = androidFacet.configuration.state
    TestCase.assertNotNull(state)
    state.ASSEMBLE_TASK_NAME = "assembleTask2"
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2")
    state.COMPILE_JAVA_TASK_NAME = "compileTask2"
    AndroidModel.set(androidFacet, androidModel)
  }

  fun testFindTasksToExecuteForAssemblingJavaModule() {
    setUpModuleAsJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":testFindTasksToExecuteForAssemblingJavaModule:assemble")
  }

  fun testFindTasksToExecuteForCompilingJavaModule() {
    setUpModuleAsJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":testFindTasksToExecuteForCompilingJavaModule:compileJava")
  }

  fun testFindTasksToExecuteForCompilingJavaModuleAndTests() {
    setUpModuleAsJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    var tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.UNIT_TESTS)
    var tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:compileJava",
      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:testClasses"
    )
    // check it also for TestCompileType.ALL
    tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.ALL)
    tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:compileJava",
      ":testFindTasksToExecuteForCompilingJavaModuleAndTests:testClasses"
    )
  }

  private fun setUpModuleAsJavaModule() {
    setUpModuleAsGradleModule()
    val javaFacet = Facets.createAndAddJavaFacet(module)
    javaFacet.configuration.BUILDABLE = true
  }

  private fun setUpModuleAsGradleModule() {
    val module = module
    setUpModuleAsGradleModule(module)
  }

  companion object {

    private fun setUpModuleAsGradleModule(module: Module) {
      val gradleFacet = Facets.createAndAddGradleFacet(module)
      gradleFacet.configuration.GRADLE_PROJECT_PATH = SdkConstants.GRADLE_PATH_SEPARATOR + module.name
      val gradlePath = SdkConstants.GRADLE_PATH_SEPARATOR + module.name
      val gradleProjectStub: GradleProject = GradleProjectStub(
        emptyList(),
        gradlePath,
        Projects.getBaseDirPath(module.project)
      )
      val model = GradleModuleModel(module.name, gradleProjectStub, emptyList(), null, null, null, null)
      gradleFacet.setGradleModuleModel(model)
    }
  }
}