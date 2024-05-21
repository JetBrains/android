package com.android.tools.idea.compose.preview.actions

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent.createTestEvent
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class PreviewSurfaceActionManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testToolbarActionsDisabledWhenPreviewHasErrors() {

    val mockSurface = mock<DesignSurface<LayoutlibSceneManager>>()
    val mockNavigationHandler = mock<NavigationHandler>()
    val actionManager = PreviewSurfaceActionManager(mockSurface, mockNavigationHandler)

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

      // EnableUiCheckAction(),
      // AnimationInspectorAction(),
      // EnableInteractiveAction(),
      // DeployToDeviceAction()
      // in wrappers
      assertEquals(4, actions.size)

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
}

private data class Status(
  override val hasErrorsAndNeedsBuild: Boolean,
  override val hasSyntaxErrors: Boolean,
  override val isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override val isRefreshing: Boolean,
  override val previewedFile: PsiFile? = null,
) : PreviewViewModelStatus
