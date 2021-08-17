/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test

private class TestBuildListener: BuildListener {
  private val log = StringBuilder()

  override fun buildStarted() {
    log.append("Build Started\n")
  }

  override fun buildSucceeded() {
    log.append("Build Succeeded\n")
  }

  override fun buildFailed() {
    log.append("Build Failed\n")
  }

  override fun buildCleaned() {
    log.append("Build Cleaned\n")
  }

  fun getLog(): String = log.toString().trimEnd()
}

private class TestProjectSystemBuildManager: ProjectSystemBuildManager {
  private val listeners = mutableListOf<ProjectSystemBuildManager.BuildListener>()
  private var lastBuildResult: ProjectSystemBuildManager.BuildResult = ProjectSystemBuildManager.BuildResult.createUnknownBuildResult()
  private var lastBuildMode = ProjectSystemBuildManager.BuildMode.UNKNOWN
  override fun getLastBuildResult(): ProjectSystemBuildManager.BuildResult = lastBuildResult

  override fun compileProject() {}

  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) {
    listeners.add(buildListener)
    Disposer.register(parentDisposable) {
      listeners.remove(buildListener)
    }
  }

  fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
    lastBuildMode = mode
    listeners.forEach {
      it.buildStarted(mode)
    }
  }

  fun buildCompleted(status: ProjectSystemBuildManager.BuildStatus) {
    lastBuildResult = ProjectSystemBuildManager.BuildResult(lastBuildMode, status, System.currentTimeMillis())
    listeners.forEach {
      it.beforeBuildCompleted(lastBuildResult)
    }
    listeners.forEach {
      it.buildCompleted(lastBuildResult)
    }
  }
}

@RunsInEdt
class BuildListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val edtRule = EdtRule()

  private val testBuildManager = TestProjectSystemBuildManager()
  private val project: Project
    get() = projectRule.project

  private fun setupBuildListener(buildMode: ProjectSystemBuildManager.BuildMode)
    : Triple<TestProjectSystemBuildManager, ProjectSystemBuildManager.BuildMode, TestBuildListener> {
    // Make sure there are no pending call before setting up the listener
    processEvents()
    val listener = TestBuildListener()
    setupBuildListener(project, listener, projectRule.fixture.testRootDisposable, buildManager = testBuildManager,
                       allowMultipleSubscriptionsPerProject = false)
    return Triple(testBuildManager,
                  buildMode,
                  listener)
  }

  private fun processEvents() = UIUtil.invokeAndWaitIfNeeded(Runnable {
    UIUtil.dispatchAllInvocationEvents()
  })

  @Test
  fun testBuildSuccessful() {
    val (buildState, buildMode, buildListener) = setupBuildListener(ProjectSystemBuildManager.BuildMode.ASSEMBLE)

    buildState.buildStarted(buildMode)
    processEvents()
    assertEquals("Build Started", buildListener.getLog())

    buildState.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()
    assertEquals("""
      Build Started
      Build Succeeded
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testBuildFailed() {
    val (buildState, buildMode, buildListener) = setupBuildListener(ProjectSystemBuildManager.BuildMode.ASSEMBLE)

    buildState.buildStarted(buildMode)
    buildState.buildCompleted(ProjectSystemBuildManager.BuildStatus.FAILED)
    processEvents()
    assertEquals("""
      Build Started
      Build Failed
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testCleanBuild() {
    val (buildState, buildMode, buildListener) = setupBuildListener(ProjectSystemBuildManager.BuildMode.CLEAN)
    buildState.buildStarted(buildMode)
    processEvents()
    assertEquals("", buildListener.getLog())

    buildState.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()
    assertEquals("Build Cleaned", buildListener.getLog())
  }

  /**
   * Regression test for b/177355531. Disposing the first build listener would remove all listeners.
   */
  @Test
  fun testRemoveSecondListener() {
    val secondListener = object : BuildListener {
      override fun buildSucceeded() {}
    }
    val secondDisposable = Disposer.newDisposable()
    setupBuildListener(project, secondListener, secondDisposable)

    Disposer.dispose(secondDisposable)

    val (buildState, buildMode, buildListener) = setupBuildListener(ProjectSystemBuildManager.BuildMode.ASSEMBLE)
    buildState.buildStarted(buildMode)
    buildState.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()
    assertEquals("""
      Build Started
      Build Succeeded
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testAllowMultipleSubscriptionsPerProject() {
    var firstListenerCalls = 0
    val firstListener = object : BuildListener {
      override fun buildStarted() {
        firstListenerCalls++
      }
    }
    val firstDisposable = Disposer.newDisposable()
    setupBuildListener(project, firstListener, firstDisposable, buildManager = testBuildManager,
                       allowMultipleSubscriptionsPerProject = false)

    var secondListenerCalls = 0
    val secondListener = object : BuildListener {
      override fun buildStarted() {
        secondListenerCalls++
      }
    }
    val secondDisposable = Disposer.newDisposable()
    setupBuildListener(project, secondListener, secondDisposable, buildManager = testBuildManager,
                       allowMultipleSubscriptionsPerProject = false)

    testBuildManager.buildStarted(ProjectSystemBuildManager.BuildMode.ASSEMBLE)
    testBuildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()

    assertEquals(1, firstListenerCalls)
    assertEquals(1, secondListenerCalls)

    Disposer.dispose(firstDisposable)

    testBuildManager.buildStarted(ProjectSystemBuildManager.BuildMode.ASSEMBLE)
    testBuildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()

    assertEquals(1, firstListenerCalls)
    // The second disposable was not disposed, but the second listener was not called since we didn't allow multiple listener subscriptions
    // per project. Therefore, after the first subscription was disposed, further subscriptions were ignored.
    assertEquals(1, secondListenerCalls)

    var thirdListenerCalls = 0
    val thirdListener = object : BuildListener {
      override fun buildStarted() {
        thirdListenerCalls++
      }
    }
    val thirdDisposable = Disposer.newDisposable()
    setupBuildListener(project, thirdListener, thirdDisposable, buildManager = testBuildManager,
                       allowMultipleSubscriptionsPerProject = true)

    testBuildManager.buildStarted(ProjectSystemBuildManager.BuildMode.COMPILE)
    testBuildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    processEvents()


    assertEquals(1, thirdListenerCalls)
    // The second disposable was not disposed, and now we're allowing multiple listener subscriptions per project. Therefore, the second
    // listener will also be called.
    assertEquals(2, secondListenerCalls)

    Disposer.dispose(secondDisposable)
    Disposer.dispose(thirdDisposable)
  }
}