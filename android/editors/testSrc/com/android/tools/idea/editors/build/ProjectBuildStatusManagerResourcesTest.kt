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

import com.android.tools.idea.editors.fast.FastPreviewRule
import com.android.tools.idea.editors.fast.simulateProjectSystemBuild
import com.android.tools.idea.editors.fast.simulateResourcesChange
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.ApplicationUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import junit.framework.Assert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.CoreMatchers
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.Executor

class ProjectBuildStatusManagerResourcesTest {
  val projectRule = AndroidProjectRule.withAndroidModel()
  val project: Project
    get() = projectRule.project

  @get:Rule
  val chainRule: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(FastPreviewRule())

  @Test
  fun testResourcesMakeTheProjectOutOfDate() {
    val psiFile = projectRule.fixture.addFileToProject("/src/a/Test.kt", "fun a() {}")
    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    // Simulate a successful build
    (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
      buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)
    Assert.assertEquals(ProjectStatus.Ready, statusManager.status)

    ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
      projectRule.fixture.openFileInEditor(psiFile.virtualFile)
    }

    // Simulate a resources change
    (statusManager as ProjectBuildStatusManagerForTests).simulateResourcesChange()
    assertThat(statusManager.status, CoreMatchers.instanceOf(ProjectStatus.OutOfDate::class.java))

    // A build should restore the ready state
    (statusManager as ProjectBuildStatusManagerForTests).simulateProjectSystemBuild(
      buildStatus = ProjectSystemBuildManager.BuildStatus.SUCCESS)
    Assert.assertEquals(ProjectStatus.Ready, statusManager.status)
  }
}