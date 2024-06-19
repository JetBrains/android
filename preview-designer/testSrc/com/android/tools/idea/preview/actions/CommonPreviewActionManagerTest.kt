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
package com.android.tools.idea.preview.actions

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.sceneview.InteractiveLabelPanel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runInEdt
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent.createTestEvent
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyBoolean

// AnimationInspectorAction(),
// EnableInteractiveAction(),
// in wrappers
private const val EXPECTED_NUMBER_OF_ACTIONS = 2

class CommonPreviewActionManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val navigationHandler = mock<NavigationHandler>()

  private val actionManager =
    CommonPreviewActionManager(
      mock(),
      navigationHandler,
      supportAnimationPreview = true,
      supportInteractivePreview = true,
    )

  @Test
  fun testToolbarActionsDisabledWhenPreviewHasErrors() {
    // Simulate different preview statuses
    val errorStatus =
      Status(hasErrorsAndNeedsBuild = true, hasSyntaxErrors = true, isRefreshing = false)
    val refreshingStatus =
      Status(hasErrorsAndNeedsBuild = false, hasSyntaxErrors = false, isRefreshing = true)
    val noErrorStatus =
      Status(hasErrorsAndNeedsBuild = false, hasSyntaxErrors = false, isRefreshing = false)

    val statusesToEnable =
      mapOf(
        errorStatus to Pair(false, "Preview has errors"),
        refreshingStatus to Pair(false, "Preview is refreshing"),
        noErrorStatus to Pair(true, null),
      )

    statusesToEnable.forEach { (status, expectedResult) ->
      val (isEnabled, description) = expectedResult

      val dataContext = DataContext {
        when (it) {
          CommonDataKeys.PROJECT.name -> projectRule.project
          PREVIEW_VIEW_MODEL_STATUS.name -> status
          else -> null
        }
      }
      val testEvent = createTestEvent(dataContext)

      val actions =
        (actionManager.getSceneViewContextToolbarActions().filterIsInstance<ActionGroup>().single())
          .getChildren(testEvent)

      assertEquals(EXPECTED_NUMBER_OF_ACTIONS, actions.size)

      actions.forEach { action ->
        action.update(testEvent)
        assertEquals(
          isEnabled,
          testEvent.presentation.isEnabled,
          "Action should be ${if (isEnabled) "enabled" else "disabled"} for status $status",
        )
        if (description != null) {
          assertEquals(description, testEvent.presentation.description)
        }
      }
    }
  }

  // Regression test for b/346911154
  @Test
  fun testToolbarActionsDisabledWhenProjectNeedsBuild() {
    val projectSystemService = projectRule.mockProjectService(ProjectSystemService::class.java)
    val androidProjectSystem = mock<AndroidProjectSystem>()
    val buildManager = mock<ProjectSystemBuildManager>()
    whenever(projectSystemService.projectSystem).thenReturn(androidProjectSystem)
    whenever(androidProjectSystem.getBuildManager()).thenReturn(buildManager)

    val noErrorStatus =
      Status(hasErrorsAndNeedsBuild = false, hasSyntaxErrors = false, isRefreshing = false)
    val dataContext = DataContext {
      when (it) {
        CommonDataKeys.PROJECT.name -> projectRule.project
        PREVIEW_VIEW_MODEL_STATUS.name -> noErrorStatus
        else -> null
      }
    }
    val testEvent = createTestEvent(dataContext)
    val actions =
      (actionManager.getSceneViewContextToolbarActions().filterIsInstance<ActionGroup>().single())
        .getChildren(createTestEvent(dataContext))
    assertEquals(EXPECTED_NUMBER_OF_ACTIONS, actions.size)

    // project needs build
    run {
      whenever(buildManager.getLastBuildResult())
        .thenReturn(
          ProjectSystemBuildManager.BuildResult(
            mode = ProjectSystemBuildManager.BuildMode.CLEAN,
            status = ProjectSystemBuildManager.BuildStatus.FAILED,
            timestampMillis = 0L,
          )
        )

      actions.forEach { action ->
        action.update(testEvent)
        assertEquals(false, testEvent.presentation.isEnabled)
        assertEquals("Project needs build", testEvent.presentation.description)
      }
    }

    // project doesn't need build
    run {
      whenever(buildManager.getLastBuildResult())
        .thenReturn(
          ProjectSystemBuildManager.BuildResult(
            mode = ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE,
            status = ProjectSystemBuildManager.BuildStatus.SUCCESS,
            timestampMillis = 0L,
          )
        )

      actions.forEach { action ->
        action.update(testEvent)
        assertEquals(true, testEvent.presentation.isEnabled)
        assertNotEquals("Project needs build", testEvent.presentation.description)
      }
    }
  }

  // Regression test for b/347711875
  @Test
  fun testClickingOnLabelNavigatesToSceneView(): Unit = runBlocking {
    val sceneManager = mock<SceneManager>()
    val sceneView = mock<SceneView>()
    val model = mock<NlModel>()
    whenever(sceneView.sceneManager).thenReturn(sceneManager)
    whenever(sceneManager.model).thenReturn(model)
    whenever(model.modelDisplayName).thenReturn(MutableStateFlow("some name"))
    whenever(model.tooltip).thenReturn(MutableStateFlow("some tooltip"))

    val invoked = CompletableDeferred<Boolean>()
    val scope = AndroidCoroutineScope(projectRule.fixture.testRootDisposable)
    whenever(navigationHandler.handleNavigate(eq(sceneView), anyBoolean())).then {
      invoked.complete(true)
    }

    val label = actionManager.createSceneViewLabel(sceneView, scope)
    assertTrue(label is InteractiveLabelPanel)
    assertEquals("some name", label.text)
    assertEquals("some tooltip", label.toolTipText)

    runInEdt { label.mouseListeners.forEach { it.mouseClicked(mock()) } }

    assertTrue(invoked.await())
  }
}

private data class Status(
  override val hasErrorsAndNeedsBuild: Boolean,
  override val hasSyntaxErrors: Boolean,
  override val isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override val isRefreshing: Boolean,
  override val previewedFile: PsiFile? = null,
) : PreviewViewModelStatus
