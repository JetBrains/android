/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Before
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

@RunsInEdt
class BuildListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val edtRule = EdtRule()

  private val project: Project
    get() = projectRule.project

  private val buildTargetReference get() = BuildTargetReference.gradleOnly(projectRule.module)

  private val buildSystemServices = FakeBuildSystemFilePreviewServices()

  @Before
  fun setUp() {
    buildSystemServices.register(projectRule.testRootDisposable)
  }

  private fun setupBuildListener(): TestBuildListener {
    // Make sure there are no pending call before setting up the listener
    processEvents()
    val listener = TestBuildListener()
    setupBuildListener(buildTargetReference, listener, projectRule.fixture.testRootDisposable)
    return listener
  }

  private fun processEvents() = UIUtil.invokeAndWaitIfNeeded(Runnable {
    UIUtil.dispatchAllInvocationEvents()
  })

  @Test
  fun testBuildSuccessful() {
    val buildListener = setupBuildListener()
    val completion = SettableFuture.create<Unit>()
    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS, completion = completion)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("Build Started")

    completion.set(Unit)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("""
      Build Started
      Build Succeeded
    """.trimIndent())
  }

  @Test
  fun testBuildFailed() {
    val buildListener = setupBuildListener()

    buildSystemServices.simulateArtifactBuild(BuildStatus.FAILED)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("""
      Build Started
      Build Failed
    """.trimIndent())
  }

  @Test
  fun testCleanBuild() {
    val completion = SettableFuture.create<Unit>()
    val buildListener = setupBuildListener()
    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS, buildMode = BuildMode.CLEAN, completion = completion)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("")

    completion.set(Unit)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("Build Cleaned")
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
    setupBuildListener(buildTargetReference, secondListener, secondDisposable)

    Disposer.dispose(secondDisposable)

    val buildListener = setupBuildListener()
    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()
    assertThat(buildListener.getLog()).isEqualTo("""
      Build Started
      Build Succeeded
    """.trimIndent())
  }

  @Test
  fun testCalledOnSubscriptionWhenPreviousBuildIsSuccessful() {
    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    var listenerCalls = 0
    val listener = object : BuildListener {
      override fun buildStarted() {
        listenerCalls++
      }
    }
    val disposable = Disposer.newDisposable()
    setupBuildListener(buildTargetReference, listener, disposable)

    assertThat(listenerCalls).isEqualTo(1)

    Disposer.dispose(disposable)
  }

  @Test
  fun testOnlyOneSubscriptionPerProject() {
    var firstListenerCalls = 0
    val firstListener = object : BuildListener {
      override fun buildStarted() {
        firstListenerCalls++
      }
    }
    val firstDisposable = Disposer.newDisposable()
    setupBuildListener(buildTargetReference, firstListener, firstDisposable)

    var secondListenerCalls = 0
    val secondListener = object : BuildListener {
      override fun buildStarted() {
        secondListenerCalls++
      }
    }
    val secondDisposable = Disposer.newDisposable()
    setupBuildListener(buildTargetReference, secondListener, secondDisposable)

    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    assertThat(firstListenerCalls).isEqualTo(1)
    assertThat(secondListenerCalls).isEqualTo(1)

    Disposer.dispose(firstDisposable)

    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    assertThat(firstListenerCalls).isEqualTo(1)
    assertThat(secondListenerCalls).isEqualTo(2)

    var thirdListenerCalls = 0
    val thirdListener = object : BuildListener {
      override fun buildStarted() {
        thirdListenerCalls++
      }
    }
    val thirdDisposable = Disposer.newDisposable()
    setupBuildListener(buildTargetReference, thirdListener, thirdDisposable)

    assertThat(thirdListenerCalls).isEqualTo(1)

    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    assertThat(secondListenerCalls).isEqualTo(3)
    assertThat(thirdListenerCalls).isEqualTo(2)

    Disposer.dispose(secondDisposable)
    Disposer.dispose(thirdDisposable)

    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    assertThat(firstListenerCalls).isEqualTo(1)
    assertThat(secondListenerCalls).isEqualTo(3)
    assertThat(thirdListenerCalls).isEqualTo(2)

    var fourthListenerCalls = 0
    val fourthListener = object : BuildListener {
      override fun buildStarted() {
        fourthListenerCalls++
      }
    }
    val fourthDisposable = Disposer.newDisposable()
    setupBuildListener(buildTargetReference, fourthListener, fourthDisposable)

    assertThat(fourthListenerCalls).isEqualTo(1)

    buildSystemServices.simulateArtifactBuild(BuildStatus.SUCCESS)
    processEvents()

    assertThat(firstListenerCalls).isEqualTo(1)
    assertThat(secondListenerCalls).isEqualTo(3)
    assertThat(thirdListenerCalls).isEqualTo(2)
    assertThat(fourthListenerCalls).isEqualTo(2)

    Disposer.dispose(fourthDisposable)
  }
}
