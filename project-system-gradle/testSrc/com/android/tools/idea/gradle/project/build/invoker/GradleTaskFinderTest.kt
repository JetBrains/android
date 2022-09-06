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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
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
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mockito
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

  fun testFindTasksToExecuteWhenLastSyncSuccessful_noModules() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject).isEmpty()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks"))
      .isEqualTo("Unable to find Gradle tasks to build: [:]. <br>Build mode: ASSEMBLE. <br>Tests: None.")
  }

  fun testFindTasksToExecuteWhenLastSyncFailed() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val syncState = Mockito.mock(GradleSyncState::class.java)
    IdeComponents(project).replaceProjectService(GradleSyncState::class.java, syncState)
    whenever(syncState.lastSyncFailed()).thenReturn(true)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    // If sync fails, try building last known Gradle projects.
    assertThat(tasksPerProject.forTest()).containsEntry(projectDir, listOf(":app:assembleDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksWithBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), buildSrcModule(":buildSrc"), buildSrcModule(":buildSrc:other"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks"))
      .isEqualTo(
        "Unable to find Gradle tasks to build: [:, :buildSrc, :buildSrc:other]. <br>Build mode: ASSEMBLE. <br>Tests: None.")
  }

  fun testFindTasksWithNonBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":buildSrc1"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsEntry(projectDir, listOf(":buildSrc1:assemble"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksWithNonGradleModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    createModule("some")
    assume().that(modules).hasLength(2)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks"))
      .isEqualTo("Unable to find Gradle tasks to build: [:]. <br>Build mode: ASSEMBLE. <br>Tests: None.")
  }

  fun testFindTasksWithEmptyGradlePath() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule())
    Facets.createAndAddGradleFacet(createModule("some"))
    assume().that(modules).hasLength(2)
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).isEmpty()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks"))
      .isEqualTo("Unable to find Gradle tasks to build: [:]. <br>Build mode: ASSEMBLE. <br>Tests: None.")
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":generateDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteWhenCleaningAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.CLEAN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:generateDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":generateDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForSourceGenerationInAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.SOURCE_GEN, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:generateDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":assembleDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForAssemblingAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:assembleDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForRebuildingAndroidProject() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.REBUILD, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":clean", // Note that the comparison is order sensitive and "clean" goes first. (b/78443416)
      ":assembleDebug"
    )).inOrder()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForRebuildingAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.REBUILD, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:clean", // Note that the comparison is order sensitive and the clean task goes first. (b/78443416)
      ":app:assembleDebug"
    )).inOrder()
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":compileDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForCompilingAndroidProject_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:compileDebugSources"
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
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
          .filter { it.getGradleProjectPath()?.path == ":app" }
          .toTypedArray(),
        BuildMode.ASSEMBLE,
        TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(
      ":app:assembleDebug",
      ":feature1:assembleDebug",
    ))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForBundleTool_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":bundleDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForBundleTool_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:bundleDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForApkFromBundle_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":extractApksForDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForApkFromBundle_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), androidModule(":app"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":app:extractApksForDebug"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForAssemblingJavaModule_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, javaModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":assemble"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForAssemblingJavaModule_nonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:assemble"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForCompilingJavaModule_rootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, javaModule(":"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":compileJava"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForCompilingJavaModule_NonRootModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.COMPILE_JAVA, TestCompileType.NONE)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:compileJava"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
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
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
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
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  fun testFindTasksToExecuteForBuildSrcModule() {
    setupTestProjectFromAndroidModel(project, projectDir, rootModule(), javaModule(":lib"), buildSrcModule(":buildSrc"))
    val tasksPerProject = taskFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, TestCompileType.ALL)
    assertThat(tasksPerProject.forTest()).containsExactly(projectDir, listOf(":lib:assemble", ":lib:testClasses"))
    assertThat(getNotification(prefix = "Unable to find Gradle tasks")).isNull()
  }

  private fun getNotification(prefix: String) =
    NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(Notification::class.java, project)
      .map { it.content }
      .singleOrNull { it.startsWith(prefix) }

}

private fun javaModule(gradlePath: String) = JavaModuleModelBuilder(gradlePath)

private fun buildSrcModule(gradlePath: String) = JavaModuleModelBuilder(gradlePath, isBuildSrc = true)

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
