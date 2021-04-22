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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.collect.Multimap
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Path

/**
 * Tests for [GradleTaskFinder].
 */
class GradleTaskFinderTest : PlatformTestCase() {

  private val modules get() = ModuleManager.getInstance(project).modules
  private val projectDir get() = File(project.basePath!!)

  private lateinit var taskFinder: GradleTaskFinder

  override fun setUp() {
    super.setUp()
    taskFinder = GradleTaskFinder.getInstance()
  }

  fun testCreateBuildTaskWithTopLevelModule() {
    val task = taskFinder.createBuildTask(":", "assemble")
    assertEquals(":assemble", task)
  }

  fun testFindTasksToExecuteWhenLastSyncSuccessful_noModules() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject).isEmpty()
  }

  fun testFindTasksToExecuteWhenLastSyncFailed() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    val syncState = Mockito.mock(GradleSyncState::class.java)
    IdeComponents(project).replaceProjectService(GradleSyncState::class.java, syncState)
    `when`(syncState.lastSyncFailed()).thenReturn(true)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf("assemble"))
  }

  fun testFindTasksWithBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":buildSrc"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
  }

  fun testFindTasksWithNonBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":buildSrc1"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsEntry(projectDir, listOf(":buildSrc1:assemble"))
  }

  fun testFindTasksWithNonGradleModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    createModule("some")
    assume().that(modules).hasLength(2)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
  }

  fun testFindTasksWithEmptyGradlePath() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    Facets.createAndAddGradleFacet(createModule("some"))
    assume().that(modules).hasLength(2)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":ideSetupTask1",
      ":ideSetupTask2"
    ))
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:ideSetupTask1",
      ":app:ideSetupTask2"
    ))
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":ideSetupTask1",
      ":ideSetupTask2"
    ))
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:ideSetupTask1",
      ":app:ideSetupTask2"
    ))
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":assembleDebug"))
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:assembleDebug"))
  }

  fun testFindTasksToExecuteForRebuildingAndroidProject() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.REBUILD, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      "clean", // Note that the comparison is order sensitive and "clean" goes first. (b/78443416)
      ":assembleDebug"
    ))
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":ideSetupTask1",
      ":ideSetupTask2",
      ":compileDebugSources"
    ))
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:ideSetupTask1",
      ":app:ideSetupTask2",
      ":app:compileDebugSources"
    ))
  }

  fun testFindTasksToExecuteForCompilingDynamicApp() {
    setupTestProjectFromAndroidModel(
      project,
      projectDir,
      rootModule(),
      androidModule(":app", dynamicFeatures = listOf(":feature1")),
      androidModule(":feature1", projectType = IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE, moduleDependencies = listOf(":app"))
    )
    val tasksPerProject = taskFinder
      .findTasksToExecute(
        modules
          .filter { it.name == "app" }
          .toTypedArray(),
        BuildMode.ASSEMBLE,
        TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":feature1:assembleDebug",
      ":app:assembleDebug"
    ))
  }

  fun testFindTasksToExecuteForBundleTool_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":bundleDebug"))
  }

  fun testFindTasksToExecuteForBundleTool_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:bundleDebug"))
  }

  fun testFindTasksToExecuteForApkFromBundle_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":extractApksForDebug"))
  }

  fun testFindTasksToExecuteForApkFromBundle_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:extractApksForDebug"))
  }

  fun testFindTasksToExecuteForAssemblingJavaModule_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, javaModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":assemble"))
  }

  fun testFindTasksToExecuteForAssemblingJavaModule_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:assemble"))
  }

  fun testFindTasksToExecuteForCompilingJavaModule_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, javaModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":compileJava"))
  }

  fun testFindTasksToExecuteForCompilingJavaModule_NonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:compileJava"))
  }

  fun testFindTasksToExecuteForCompilingJavaModuleAndTests_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, javaModule(":"))
    var tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.UNIT_TESTS)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":compileJava",
      ":testClasses"
    ))
    // check it also for TestCompileType.ALL
    tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.ALL)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":compileJava",
      ":testClasses"
    ))
  }

  fun testFindTasksToExecuteForCompilingJavaModuleAndTests_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"))
    var tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.UNIT_TESTS)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":lib:compileJava",
      ":lib:testClasses"
    ))
    // check it also for TestCompileType.ALL
    tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.ALL)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":lib:compileJava",
      ":lib:testClasses"
    ))
  }

  fun testFindTasksToExecuteForBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"), javaModule(":buildSrc"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.ALL)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:assemble", ":lib:testClasses"))
  }
}

private fun javaModule(gradlePath: String) = JavaModuleModelBuilder(gradlePath)

private fun Multimap<Path, String>.forTest() = asMap().mapKeys { it.key.toFile() }

private fun rootModule() = JavaModuleModelBuilder.rootModuleBuilder

private fun androidModule(
  gradlePath: String,
  projectType: IdeAndroidProjectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
  moduleDependencies: List<String> = emptyList(),
  dynamicFeatures: List<String> = emptyList()
) = AndroidModuleModelBuilder(
  gradlePath,
  "debug",
  AndroidProjectBuilder(
    projectType = { projectType },
    androidModuleDependencyList = { moduleDependencies.map { AndroidModuleDependency(it, "debug") } },
    dynamicFeatures = { dynamicFeatures }
  )
)
