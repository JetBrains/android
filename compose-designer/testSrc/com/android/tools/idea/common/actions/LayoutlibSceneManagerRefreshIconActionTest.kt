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
package com.android.tools.idea.common.actions

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class LayoutlibSceneManagerRefreshIconActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()
  private val project
    get() = projectRule.project

  private fun LayoutlibSceneManagerRefreshIconAction.updateAndRun(runnable: (event: AnActionEvent) -> Unit) {
    // Ensure all events have been processed
    UIUtil.dispatchAllInvocationEvents()
    val testEvent = TestActionEvent(this)
    update(testEvent)
    runnable(testEvent)
  }

  @RunsInEdt
  @Test
  fun `build triggers refresh update`() {
    val action = LayoutlibSceneManagerRefreshIconAction.forTesting(project, { }, projectRule.fixture.testRootDisposable)
    action.updateAndRun {
      assertFalse(it.presentation.isVisible)
    }

    val buildContext = BuildContext(project, listOf(), BuildMode.ASSEMBLE)
    // Start build
    GradleBuildState.getInstance(project).buildStarted(buildContext)
    action.updateAndRun {
      assertTrue(it.presentation.isVisible)
    }

    // Build failed
    GradleBuildState.getInstance(project).buildFinished(BuildStatus.FAILED)
    action.updateAndRun {
      assertFalse(it.presentation.isVisible)
    }

    // Start a second build
    GradleBuildState.getInstance(project).buildStarted(buildContext)
    action.updateAndRun {
      assertTrue(it.presentation.isVisible)
    }
  }

  @RunsInEdt
  @Test
  fun `build triggers when scene manager refreshes`() {
    val renderListeners = mutableListOf<RenderListener>()
    val action = LayoutlibSceneManagerRefreshIconAction.forTesting(project, { renderListeners.add(it) },
                                                                   projectRule.fixture.testRootDisposable)
    action.updateAndRun {
      assertFalse(it.presentation.isVisible)
    }

    renderListeners.forEach { it.onRenderStarted() }
    action.updateAndRun {
      assertTrue(it.presentation.isVisible)
    }

    renderListeners.forEach { it.onRenderCompleted() }
    action.updateAndRun {
      assertFalse(it.presentation.isVisible)
    }
  }
}