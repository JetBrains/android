package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent.createTestEvent
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// SavePreviewInNewSize()
// RevertToOriginalSize()
// EnableUiCheckAction(),
// AnimationInspectorAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction()
// in wrappers
private const val EXPECTED_NUMBER_OF_ACTIONS = 6

// EnableUiCheckAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction()
// in wrappers
private const val EXPECTED_VISIBLE_ACTIONS = 3

class PreviewSurfaceActionManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val actionManager = PreviewSurfaceActionManager(mock(), mock())

  @Test
  fun testToolbarActionsDisabledWhenPreviewHasErrors() {
    // Simulate different preview statuses
    val errorStatus = Status(hasRenderErrors = true, hasSyntaxErrors = true, isRefreshing = false)
    val refreshingStatus =
      Status(hasRenderErrors = false, hasSyntaxErrors = false, isRefreshing = true)
    val noErrorStatus =
      Status(hasRenderErrors = false, hasSyntaxErrors = false, isRefreshing = false)

    val statusesToEnable =
      mapOf(
        errorStatus to Pair(false, "Preview has errors"),
        refreshingStatus to Pair(false, "Preview is refreshing"),
        noErrorStatus to Pair(true, null),
      )

    statusesToEnable.forEach { (status, expectedResult) ->
      val (isEnabled, description) = expectedResult

      val dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, projectRule.project)
          .add(PREVIEW_VIEW_MODEL_STATUS, status)
          .build()
      val testEvent = createTestEvent(dataContext)

      val actions =
        (actionManager.getSceneViewContextToolbarActions().filterIsInstance<ActionGroup>().single())
          .getChildren(testEvent)

      assertEquals(EXPECTED_NUMBER_OF_ACTIONS, actions.size)

      val visibleActions =
        actions.filter {
          it.update(testEvent)
          testEvent.presentation.isVisible
        }

      assertThat(visibleActions).hasSize(EXPECTED_VISIBLE_ACTIONS)

      visibleActions.forEach { action ->
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
      Status(hasRenderErrors = false, hasSyntaxErrors = false, isRefreshing = false)
    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(PREVIEW_VIEW_MODEL_STATUS, noErrorStatus)
        .build()
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
          )
        )

      actions.forEach { action ->
        action.update(testEvent)
        if (testEvent.presentation.isVisible) {
          assertEquals(false, testEvent.presentation.isEnabled)
        }
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
          )
        )

      val visibleActions =
        actions.filter {
          it.update(testEvent)
          testEvent.presentation.isVisible
        }

      assertThat(visibleActions).hasSize(EXPECTED_VISIBLE_ACTIONS)

      visibleActions.forEach { action ->
        action.update(testEvent)
        assertEquals(true, testEvent.presentation.isEnabled)
        assertNotEquals("Project needs build", testEvent.presentation.description)
      }
    }
  }
}

private data class Status(
  override val hasRenderErrors: Boolean,
  override val hasSyntaxErrors: Boolean,
  override val isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override val isRefreshing: Boolean,
  override val previewedFile: PsiFile? = null,
) : PreviewViewModelStatus
