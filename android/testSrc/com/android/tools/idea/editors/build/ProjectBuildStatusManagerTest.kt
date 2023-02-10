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
import com.android.tools.idea.editors.fast.BlockingDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewRule
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.editors.fast.simulateProjectSystemBuild
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.ui.ApplicationUtils.invokeWriteActionAndWait
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

class ProjectBuildStatusManagerTest {
  val projectRule = AndroidProjectRule.inMemory()
  val project: Project
    get() = projectRule.project

  @get:Rule
  val chainRule: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(FastPreviewRule())

  @Test
  fun testFastPreviewTriggersCompileState() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val blockingDaemon = BlockingDaemonClient()
    val fastPreviewManager = FastPreviewManager.getTestInstance(project, { _, _, _, _ -> blockingDaemon }).also {
      Disposer.register(projectRule.fixture.testRootDisposable, it)
    }
    projectRule.replaceProjectService(FastPreviewManager::class.java, fastPreviewManager)

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

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

      // Launch additional requests
      repeat(10) {
        asyncScope.launch(AndroidDispatchers.workerThread) {
          fastPreviewManager.compileRequest(psiFile, module)
          latch.countDown()
        }
      }
      blockingDaemon.complete()
      latch.await()
      Assert.assertFalse(statusManager.isBuilding)
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsUpToDateForSuccessfulGradleBuild() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
        buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)

      assertEquals(ProjectStatus.Ready, statusManager.status)

      // Disabling Live Edit will bring the out of date state
      FastPreviewManager.getInstance(project).disable(ManualDisabledReason)
      assertEquals(ProjectStatus.Ready, statusManager.status)
    }
    finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsOutOfDateForFailedGradleBuild() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
        buildStatus = ProjectSystemBuildManager.BuildStatus.FAILED)

      assertEquals(ProjectStatus.NeedsBuild, statusManager.status)

      // Disabling Live Edit will bring the out of date state
      FastPreviewManager.getInstance(project).disable(ManualDisabledReason)
      assertEquals(ProjectStatus.NeedsBuild, statusManager.status)
    }
    finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }

  @Test
  fun testFastPreviewEnableLeavesFileAsOutOfDateForFailedFastPreviewCompilation() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    try {
      FastPreviewManager.getInstance(project).enable()

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
        buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)

      assertEquals(ProjectStatus.Ready, statusManager.status)

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
      Assert.assertThat(statusManager.status, CoreMatchers.instanceOf(ProjectStatus.OutOfDate::class.java))
    }
    finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }
}