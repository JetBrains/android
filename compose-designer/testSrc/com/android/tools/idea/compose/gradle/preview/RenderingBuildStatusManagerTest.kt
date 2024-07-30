/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.editors.fast.DisableReason
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class RenderingBuildStatusManagerTest {
  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  val project: Project
    get() = projectRule.project

  @Before
  fun setup() {
    LiveEditApplicationConfiguration.getInstance().mode =
      LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
    FastPreviewManager.getInstance(project)
      .disable(DisableReason("Disabled for Live Literals testing"))
  }

  @After
  fun tearDown() {
    FastPreviewConfiguration.getInstance().resetDefault()
  }

  @Ignore("b/355654431")
  @RunsInEdt
  @Test
  fun testProjectStatusManagerStates() = runBlocking {
    val mainFile =
      projectRule.project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    WriteAction.run<Throwable> { projectRule.fixture.openFileInEditor(mainFile) }

    var onReadyCalled = false
    val statusManager =
      RenderingBuildStatusManager.create(
        projectRule.fixture.testRootDisposable,
        projectRule.fixture.file,
        scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()),
        onReady = { onReadyCalled = true },
      )
    statusManager.statusFlow.awaitStatus("Ready state expected", 5.seconds) {
      it == RenderingBuildStatus.Ready
    }
    assertTrue(onReadyCalled)
    assertTrue("Project must compile correctly", projectRule.build().isBuildSuccessful)
    statusManager.statusFlow.awaitStatus(
      "Builds status is not Ready after successful build",
      5.seconds,
    ) {
      it == RenderingBuildStatus.Ready
    }

    // Status of files created after a build should be NeedsBuild until a new build happens
    val newFile =
      projectRule.fixture.addFileToProject(
        "${SimpleComposeAppPaths.APP_SIMPLE_APPLICATION_DIR}/newFile",
        "",
      )
    val newStatusManager =
      RenderingBuildStatusManager.create(
        projectRule.fixture.testRootDisposable,
        newFile,
        scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()),
      )
    newStatusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }
    projectRule.buildAndAssertIsSuccessful()
    newStatusManager.statusFlow.awaitStatus("Ready state expected", 5.seconds) {
      it == RenderingBuildStatus.Ready
    }

    // Modifying a separate file should make both status managers out of date
    val documentManager = PsiDocumentManager.getInstance(projectRule.project)
    WriteCommandAction.runWriteCommandAction(project) {
      documentManager
        .getDocument(projectRule.fixture.file)!!
        .insertString(0, "\n\nfun method() {}\n\n")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    statusManager.statusFlow.awaitStatus("OutOfDate state expected", 5.seconds) {
      it is RenderingBuildStatus.OutOfDate
    }
    newStatusManager.statusFlow.awaitStatus("OutOfDate state expected", 5.seconds) {
      it is RenderingBuildStatus.OutOfDate
    }

    // Status should change to NeedsBuild for all managers after a build clean
    projectRule.clean()
    statusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }
    newStatusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }
  }

  @RunsInEdt
  @Test
  fun testProjectStatusManagerStatesFailureModes() = runBlocking {
    val mainFile =
      projectRule.project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!

    val documentManager = PsiDocumentManager.getInstance(projectRule.project)

    // Force clean
    projectRule.clean()
    WriteCommandAction.runWriteCommandAction(project) {
      projectRule.fixture.openFileInEditor(mainFile)

      // Break the compilation
      documentManager.getDocument(projectRule.fixture.file)!!.insertString(0, "<<Invalid>>")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    val statusManager =
      RenderingBuildStatusManager.create(
        projectRule.fixture.testRootDisposable,
        projectRule.fixture.file,
        scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()),
      )
    statusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }
    assertFalse(projectRule.build().isBuildSuccessful)
    statusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }

    WriteCommandAction.runWriteCommandAction(project) {
      // Fix the build
      documentManager.getDocument(projectRule.fixture.file)!!.deleteString(0, "<<Invalid>>".length)
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    val facet = projectRule.androidFacet(":app")
    waitForResourceRepositoryUpdates(facet.module.getMainModule())
    statusManager.statusFlow.awaitStatus("NeedsBuild state expected", 5.seconds) {
      it == RenderingBuildStatus.NeedsBuild
    }
    projectRule.buildAndAssertIsSuccessful()
    statusManager.statusFlow.awaitStatus(
      "Builds status is not Ready after successful build",
      5.seconds,
    ) {
      it == RenderingBuildStatus.Ready
    }
  }
}
