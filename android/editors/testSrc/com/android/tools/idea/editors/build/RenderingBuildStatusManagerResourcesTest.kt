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

import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.fast.simulateResourcesChange
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.ApplicationUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

class RenderingBuildStatusManagerResourcesTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel()
  val project: Project
    get() = projectRule.project

  val buildServices = FakeBuildSystemFilePreviewServices()

  @Before
  fun setup() {
    buildServices.register(projectRule.testRootDisposable)
  }

  @Test
  fun testResourcesMakeTheProjectOutOfDate() = runBlocking {
    val psiFile = projectRule.fixture.addFileToProject("/src/a/Test.kt", "fun a() {}")
    val statusManager = RenderingBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    // Simulate a successful build
    buildServices.simulateArtifactBuild(buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)
    statusManager.statusFlow.awaitStatus(
      "Ready state expected",
      5.seconds
    ) { it == RenderingBuildStatus.Ready }

    ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
      projectRule.fixture.openFileInEditor(psiFile.virtualFile)
    }

    // Simulate a resources change
    (statusManager as RenderingBuildStatusManagerForTests).simulateResourcesChange()
    statusManager.statusFlow.awaitStatus(
      "OutOfDate expected after a resource change",
      5.seconds
    ) { it is RenderingBuildStatus.OutOfDate }

    // A build should restore the ready state
    buildServices.simulateArtifactBuild(buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)
    statusManager.statusFlow.awaitStatus(
      "Ready state expected",
      5.seconds
    ) { it == RenderingBuildStatus.Ready }
  }
}
