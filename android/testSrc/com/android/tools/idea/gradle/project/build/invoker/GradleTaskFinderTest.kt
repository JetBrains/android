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
import com.intellij.openapi.module.ModuleManager
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
    Mockito.`when`(rootPathFinder.getProjectRootPath(Mockito.any())).thenReturn(Paths.get(projectRootPath))
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

  fun testFindTasksToExecuteWhenCleaningAndroidProject_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":afterSyncTask1",
      ":afterSyncTask2",
      ":ideSetupTask1",
      ":ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":app:afterSyncTask1",
      ":app:afterSyncTask2",
      ":app:ideSetupTask1",
      ":app:ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":afterSyncTask1",
      ":afterSyncTask2",
      ":ideSetupTask1",
      ":ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":app:afterSyncTask1",
      ":app:afterSyncTask2",
      ":app:ideSetupTask1",
      ":app:ideSetupTask2"
    )
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":assembleTask1",
      ":assembleTask2"
    )
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":app:assembleTask1",
      ":app:assembleTask2"
    )
  }

  fun testFindTasksToExecuteForRebuildingAndroidProject() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.REBUILD, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      "clean",
      ":assembleTask1",
      ":assembleTask2"
    )
    // Make sure clean is the first task (b/78443416)
    Truth.assertThat(tasks[0]).isEqualTo("clean")
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":compileTask1",
      ":compileTask2",
      ":ideSetupTask1",
      ":ideSetupTask2",
      ":afterSyncTask1",
      ":afterSyncTask2"
    )
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":app:compileTask1",
      ":app:compileTask2",
      ":app:ideSetupTask1",
      ":app:ideSetupTask2",
      ":app:afterSyncTask1",
      ":app:afterSyncTask2"
    )
  }

  fun testFindTasksToExecuteForCompilingDynamicApp() {
    setUpModuleAsRootAndroidModule()
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
      ":assembleTask1",
      ":assembleTask2",
      ":feature1:assembleTask1",
      ":feature1:assembleTask2"
    )
  }

  fun testFindTasksToExecuteForBundleTool_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":bundleTask1")
  }

  fun testFindTasksToExecuteForBundleTool_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":app:bundleTask1")
  }

  fun testFindTasksToExecuteForApkFromBundle_rootModule() {
    setUpModuleAsRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":apkFromBundleTask1")
  }

  fun testFindTasksToExecuteForApkFromBundle_nonRootModule() {
    setUpModuleAsNonRootAndroidModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":app:apkFromBundleTask1")
  }

  fun testFindTasksToExecuteForAssemblingJavaModule() {
    setUpModuleAsRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":assemble")
  }

  fun testFindTasksToExecuteForAssemblingNonRootJavaModule() {
    setUpModuleAsNonRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":lib:assemble")
  }

  fun testFindTasksToExecuteForCompilingRootJavaModule() {
    setUpModuleAsRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":compileJava")
  }

  fun testFindTasksToExecuteForCompilingNonRootJavaModule() {
    setUpModuleAsNonRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, testCompileType)
    val tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(":lib:compileJava")
  }

  fun testFindTasksToExecuteForCompilingJavaModuleAndTests_rootModule() {
    setUpModuleAsRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    var tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.UNIT_TESTS)
    var tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":compileJava",
      ":testClasses"
    )
    // check it also for TestCompileType.ALL
    tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.ALL)
    tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":compileJava",
      ":testClasses"
    )
  }

  fun testFindTasksToExecuteForCompilingJavaModuleAndTests_nonRootModule() {
    setUpModuleAsNonRootJavaModule()
    val projectPath = Projects.getBaseDirPath(project)
    var tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.UNIT_TESTS)
    var tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":lib:compileJava",
      ":lib:testClasses"
    )
    // check it also for TestCompileType.ALL
    tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.ALL)
    tasks = tasksPerProject[projectPath.toPath()]
    Truth.assertThat(tasks).containsExactly(
      ":lib:compileJava",
      ":lib:testClasses"
    )
  }

  private fun setUpModuleAsRootJavaModule() {
    val module = module
    setUpModuleAsGradleModule(module, isJavaModule = true)
    val javaFacet = Facets.createAndAddJavaFacet(module)
    javaFacet.configuration.BUILDABLE = true
  }

  private fun setUpModuleAsNonRootJavaModule() {
    val module = createModule("lib")
    modules = ModuleManager.getInstance(project).modules
    setUpModuleAsGradleModule(module, isJavaModule = true)
    val javaFacet = Facets.createAndAddJavaFacet(module)
    javaFacet.configuration.BUILDABLE = true
  }

  private fun setUpModuleAsRootAndroidModule() {
    setUpModuleAsAndroidModule(module, androidModel, ideAndroidProject)
  }

  private fun setUpModuleAsNonRootAndroidModule() {
    val module = createModule("app")
    modules = ModuleManager.getInstance(project).modules
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

  companion object {

    private fun setUpModuleAsGradleModule(module: Module, isBuildable: Boolean = true, isJavaModule: Boolean = false) {
      // The following statements describe the way the modules are set up by sync:
      // Root Java modules do not get their GradlePath set unless there is something to build.
      // Root Android modules get their path set correctly to ":".
      // Non Java or Android modules (no plugins applied) should not get a Gradle facet at all.
      val gradleFacet = Facets.createAndAddGradleFacet(module)
      val gradlePath = when {
        module.name == module.project.name && isJavaModule && !isBuildable -> null
        module.name == module.project.name -> ":"
        else -> SdkConstants.GRADLE_PATH_SEPARATOR + module.name
      }
      gradleFacet.configuration.GRADLE_PROJECT_PATH = gradlePath
      val gradleProjectStub: GradleProject? = gradlePath?.let {
        GradleProjectStub(
          emptyList(),
          gradlePath,
          Projects.getBaseDirPath(module.project)
        )
      }
      if (gradleProjectStub != null) {
        val model = GradleModuleModel(module.name, gradleProjectStub, emptyList(), null, null, null, null)
        gradleFacet.setGradleModuleModel(model)
      }
    }
  }
}