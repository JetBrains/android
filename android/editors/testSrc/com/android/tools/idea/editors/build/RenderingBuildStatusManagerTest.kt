/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.build

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.fast.BlockingDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.editors.fast.simulateProjectSystemBuild
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.ui.ApplicationUtils.invokeWriteActionAndWait
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private fun RenderingBuildStatusManager.awaitReady(timeout: Duration = 5.seconds) = runBlocking {
  statusFlow.awaitStatus("ProjectStatus is not Ready after $timeout", timeout) {
    it == RenderingBuildStatus.Ready
  }
}

private fun RenderingBuildStatusManager.awaitNeedsBuild(
    message: String? = null,
    timeout: Duration = 5.seconds
) = runBlocking {
  statusFlow.awaitStatus("ProjectStatus is not NeedsBuild after $timeout", timeout) {
    it == RenderingBuildStatus.NeedsBuild
  }
}

private fun RenderingBuildStatusManager.awaitOutOfDate(
    message: String? = null,
    timeout: Duration = 5.seconds
) = runBlocking {
  statusFlow.awaitStatus("ProjectStatus is not OutOfDate after $timeout", timeout) {
    it is RenderingBuildStatus.OutOfDate
  }
}

class RenderingBuildStatusManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  val project: Project
    get() = projectRule.project

  private val execution = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test", 1)
  private lateinit var dispatcher: CoroutineDispatcher

  @Before
  fun setUp() {
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)
    dispatcher = execution.asCoroutineDispatcher()
  }

  @After
  fun tearDown() {
    execution.shutdown()
    execution.awaitTermination(1, TimeUnit.MINUTES)
  }

  @Test
  fun testFastPreviewTriggersCompileState() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val blockingDaemon = BlockingDaemonClient()
    val fastPreviewManager =
        FastPreviewManager.getTestInstance(project, { _, _, _, _ -> blockingDaemon }).also {
          Disposer.register(projectRule.fixture.testRootDisposable, it)
        }
    projectRule.replaceProjectService(FastPreviewManager::class.java, fastPreviewManager)

    val statusManager =
        RenderingBuildStatusManager.create(
            projectRule.fixture.testRootDisposable,
            psiFile,
        )

    runBlocking {
      val module = projectRule.fixture.module
      val asyncScope = AndroidCoroutineScope(projectRule.fixture.testRootDisposable)
      val latch = CountDownLatch(11)
      asyncScope.launch(AndroidDispatchers.workerThread) {
        fastPreviewManager.compileRequest(psiFile, module)
        latch.countDown()
      }
      blockingDaemon.firstRequestReceived.await()
      Assert.assertTrue(statusManager.isBuilding)
      blockingDaemon.completeOneRequest()

      // Launch additional requests
      repeat(10) {
        asyncScope.launch(AndroidDispatchers.workerThread) {
          fastPreviewManager.compileRequest(psiFile, module)
          latch.countDown()
        }
      }
      asyncScope.launch(AndroidDispatchers.workerThread) {
        repeat(10) {
          blockingDaemon.completeOneRequest()
        }
      }
      latch.await()
      Assert.assertFalse(statusManager.isBuilding)
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsUpToDateForSuccessfulGradleBuild() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager =
        RenderingBuildStatusManager.create(
            projectRule.fixture.testRootDisposable,
            psiFile,
            dispatcher = dispatcher,
        )

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
          buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)

      statusManager.awaitReady()

      // Disabling Live Edit will bring the out of date state
      FastPreviewManager.getInstance(project).disable(ManualDisabledReason)
      statusManager.awaitReady()
    } finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsOutOfDateForFailedGradleBuild() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager =
        RenderingBuildStatusManager.create(
            projectRule.fixture.testRootDisposable,
            psiFile,
            dispatcher = dispatcher,
        )

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
          buildStatus = ProjectSystemBuildManager.BuildStatus.FAILED)

      statusManager.awaitNeedsBuild()

      // Disabling Live Edit will bring the out of date state
      FastPreviewManager.getInstance(project).disable(ManualDisabledReason)
      statusManager.awaitNeedsBuild()
    } finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsOutOfDateForFailedFastPreviewCompilation() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager =
        RenderingBuildStatusManager.create(
            projectRule.fixture.testRootDisposable,
            psiFile,
            dispatcher = dispatcher,
        )

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
          buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)

      statusManager.awaitReady()

      // Add an error that will fail a compilation
      invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.openFileInEditor(psiFile.virtualFile)
      }
      runBlocking {
        WriteCommandAction.runWriteCommandAction(project) {
          projectRule.fixture.editor.executeAndSave { insertText("BrokenText") }
        }
        FastPreviewManager.getInstance(project).compileRequest(psiFile, projectRule.fixture.module)
      }

      // Disabling Live Edit will bring the out of date state
      FastPreviewManager.getInstance(project).disable(ManualDisabledReason)
      statusManager.awaitOutOfDate()
    } finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }
}