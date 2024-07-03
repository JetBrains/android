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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemBuildManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.collect.EnumMultiset
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private class TestBuildResultListener(val buildManager: ProjectSystemBuildManager) : ProjectSystemBuildManager.BuildListener {
  private var beforeBuildCompletedResult: ProjectSystemBuildManager.BuildResult? = null

  val startedBuildMode = EnumMultiset.create(ProjectSystemBuildManager.BuildMode::class.java)!!
  val completedBuilds = mutableListOf<ProjectSystemBuildManager.BuildResult>()

  override fun beforeBuildCompleted(result: ProjectSystemBuildManager.BuildResult) {
    @Suppress("DEPRECATION")
    assertTrue(buildManager.isBuilding)
    beforeBuildCompletedResult = result
  }

  override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
    @Suppress("DEPRECATION")
    assertFalse(buildManager.isBuilding)
    assertEquals(
      "beforeBuildCompleted must be called before buildCompleted with the same result",
      beforeBuildCompletedResult, result
    )
    completedBuilds.add(result)

    beforeBuildCompletedResult = null
  }

  override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
    assertTrue(buildManager.isBuilding)
    startedBuildMode.add(mode)
  }

  fun assertNoCalls() {
    assertTrue(
      "No calls expected but got $this",
      completedBuilds.isEmpty()
        && beforeBuildCompletedResult == null
        && startedBuildMode.isEmpty()
    )
  }

  fun completedBuildsDebugString(): String = completedBuilds.joinToString("\n") { "[${it.mode}] ${it.status}" }

  override fun toString(): String = "startedBuildMode = $startedBuildMode, " +
    "beforeBuildCompletedResult = $beforeBuildCompletedResult, " +
    "completedBuildsDebugString = ${completedBuildsDebugString()}"
}

class GradleProjectSystemBuildManagerTest : HeavyPlatformTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var buildManager: ProjectSystemBuildManager


  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(project)

    ideComponents.mockProjectService(GradleDependencyManager::class.java)

    buildManager = GradleProjectSystemBuildManager(project)
  }

  fun testGetLastBuildResult_unknownIfNeverSynced() {
    assertEquals(
      ProjectSystemBuildManager.BuildStatus.UNKNOWN,
      buildManager.getLastBuildResult().status
    )
  }

  fun testGetLastBuildResult_sameAsBuildResult() {
    val request = GradleBuildInvoker.Request.builder(project, projectDirOrFile.toFile(), listOf("assembleDebug"))
      .setMode(BuildMode.ASSEMBLE)
      .build()
    val completer = GradleBuildState.getInstance(project).buildStarted(BuildContext(request))
    completer.buildFinished(BuildStatus.SUCCESS)
    assertEquals(
      ProjectSystemBuildManager.BuildStatus.SUCCESS,
      buildManager.getLastBuildResult().status
    )
  }

  fun testBuildResultListener_success() {
    val listener = TestBuildResultListener(buildManager)
    buildManager.addBuildListener(testRootDisposable, listener)
    listener.assertNoCalls()
    val request = GradleBuildInvoker.Request.builder(project, projectDirOrFile.toFile(), listOf("assembleDebug"))
      .setMode(BuildMode.ASSEMBLE)
      .build()
    val completer = GradleBuildState.getInstance(project).buildStarted(BuildContext(request))
    completer.buildFinished(BuildStatus.SUCCESS)
    assertEquals(
      ProjectSystemBuildManager.BuildStatus.SUCCESS,
      buildManager.getLastBuildResult().status
    )
    assertEquals(1, listener.startedBuildMode.count(ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE))
    assertEquals("[COMPILE_OR_ASSEMBLE] SUCCESS", listener.completedBuildsDebugString())
  }

  fun testBuildResultListener_failed() {
    val listener = TestBuildResultListener(buildManager)
    buildManager.addBuildListener(testRootDisposable, listener)
    listener.assertNoCalls()
    val request = GradleBuildInvoker.Request.builder(project, projectDirOrFile.toFile(), listOf("assembleDebug"))
      .setMode(BuildMode.ASSEMBLE)
      .build()
    val completer = GradleBuildState.getInstance(project).buildStarted(BuildContext(request))
    completer.buildFinished(BuildStatus.FAILED)
    assertEquals(
      ProjectSystemBuildManager.BuildStatus.FAILED,
      buildManager.getLastBuildResult().status
    )
    assertEquals(1, listener.startedBuildMode.count(ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE))
    assertEquals("[COMPILE_OR_ASSEMBLE] FAILED", listener.completedBuildsDebugString())
  }

  fun testBuildResultListener_clean() {
    val listener = TestBuildResultListener(buildManager)
    buildManager.addBuildListener(testRootDisposable, listener)
    listener.assertNoCalls()
    val request = GradleBuildInvoker.Request.builder(project, projectDirOrFile.toFile(), listOf("clean"))
      .setMode(BuildMode.CLEAN)
      .build()
    val completer = GradleBuildState.getInstance(project).buildStarted(BuildContext(request))
    completer.buildFinished(BuildStatus.SUCCESS)
    assertEquals(
      ProjectSystemBuildManager.BuildStatus.SUCCESS,
      buildManager.getLastBuildResult().status
    )
    // Build started does not happen for clean builds
    assertEquals(1, listener.startedBuildMode.count(ProjectSystemBuildManager.BuildMode.CLEAN))
    assertEquals("[CLEAN] SUCCESS", listener.completedBuildsDebugString())
  }

  @Suppress("DEPRECATION") // For isBuilding usages
  fun testIsBuilding_withoutListeners() {
    assertFalse(buildManager.isBuilding)

    val request = GradleBuildInvoker.Request.builder(project, projectDirOrFile.toFile(), listOf("assembleDebug"))
      .setMode(BuildMode.ASSEMBLE)
      .build()
    val completer = GradleBuildState.getInstance(project).buildStarted(BuildContext(request))
    assertTrue(buildManager.isBuilding)
    completer.buildFinished(BuildStatus.SUCCESS)

    assertFalse(buildManager.isBuilding)
  }
}
