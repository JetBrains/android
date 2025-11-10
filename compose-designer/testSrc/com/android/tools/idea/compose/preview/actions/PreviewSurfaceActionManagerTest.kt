package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.EnableUnderConditionWrapper
import com.android.tools.idea.common.util.ShowGroupUnderConditionWrapper
import com.android.tools.idea.common.util.ShowUnderConditionWrapper
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.android.tools.idea.compose.preview.actions.glasses.GlassesBlendDropdownAction
import com.android.tools.idea.compose.preview.util.FakeStudioBotActionFactory
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.AnimationInspectorAction
import com.android.tools.idea.preview.actions.BackNavigationAction
import com.android.tools.idea.preview.actions.EnableInteractiveAction
import com.android.tools.idea.preview.actions.JumpToDefinitionAction
import com.android.tools.idea.preview.actions.ViewInFocusModeAction
import com.android.tools.idea.preview.actions.ZoomToSelectionAction
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent.createTestEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// CopyResultImageAction()
// ZoomToSelectionAction(),
// JumpToDefinitionAction(),
// ViewInFocusModeAction(),
// Separator(),
// SavePreviewInNewSizeAction(),
// EnableUiCheckAction(),
// AnimationInspectorAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction(),
// BackNavigationAction()
// ComposePreviewAgentsDropdownAction() or TransformPreviewAction(), depending on flag value.
// in wrappers
private const val EXPECTED_NUMBER_OF_ACTIONS = 8

// SavePreviewInNewSize()
// EnableUiCheckAction(),
// AnimationInspectorAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction()
// in wrappers
private const val EXPECTED_SCENE_VIEW_TOOLBAR_NUMBER_OF_ACTIONS = 5

// EnableUiCheckAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction()
// in wrappers
private const val EXPECTED_SCENE_VIEW_TOOLBAR_VISIBLE_ACTIONS = 3

class PreviewSurfaceActionManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val surface: DesignSurface<LayoutlibSceneManager> = mock()
  private lateinit var actionManager: PreviewSurfaceActionManager

  @Before
  fun setUp() {
    whenever(surface.interactionPane).thenReturn(mock())
    actionManager = PreviewSurfaceActionManager(surface, mock())
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI.clearOverride()
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.clearOverride()
    StudioFlags.COMPOSE_PREVIEW_AI_AGENTS_DROPDOWN.clearOverride()
  }

  @Test
  fun testCoordinateConversion() {
    val interactionPane = JPanel().apply { bounds = java.awt.Rectangle(0, 0, 500, 800) }
    val mouseSource = JPanel().apply { bounds = java.awt.Rectangle(10, 20, 100, 200) }
    interactionPane.add(mouseSource)
    whenever(surface.interactionPane).thenReturn(interactionPane)
    val mouseEvent = MouseEvent(mouseSource, 0, 0L, 0, 123, 456, 1, true)

    val actions = actionManager.getPopupMenuActions(null, mouseEvent)
    val zoomAction = actions.getChildren(null).filterIsInstance<ZoomToSelectionAction>().single()

    // Check that the point has been converted to the interactionPane coordinates
    assertThat(zoomAction.x).isEqualTo(123 + 10)
    assertThat(zoomAction.y).isEqualTo(456 + 20)
  }

  @Test
  fun testAvailableActionsOnPreviewContextMenuWithDropdownEnabled() {
    testAvailableActionsOnPreviewContextMenu(true)
  }

  @Test
  fun testAvailableActionsOnPreviewContextMenuWithDropdownDisabled() {
    testAvailableActionsOnPreviewContextMenu(false)
  }

  fun testAvailableActionsOnPreviewContextMenu(aiActionsDropdownEnabled: Boolean) {
    StudioFlags.COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI.override(true)
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.override(true)
    StudioFlags.COMPOSE_PREVIEW_AI_AGENTS_DROPDOWN.override(aiActionsDropdownEnabled)
    ExtensionTestUtil.maskExtensions(
      ComposeStudioBotActionFactory.EP_NAME,
      listOf(FakeStudioBotActionFactory()),
      projectRule.testRootDisposable,
    )

    val menuGroup = actionManager.getPopupMenuActions(null, fakeMouseEvent)

    val actions = menuGroup.getChildren(null)
    assertThat(actions.size).isEqualTo(EXPECTED_NUMBER_OF_ACTIONS)
    assertThat(actions[0]).isInstanceOf(CopyResultImageAction::class.java)
    assertThat(actions[1]).isInstanceOf(ZoomToSelectionAction::class.java)
    assertThat(actions[2]).isInstanceOf(JumpToDefinitionAction::class.java)
    assertThat(actions[3]).isInstanceOf(ViewInFocusModeAction::class.java)

    assertThat(actions[4]).isInstanceOf(Separator::class.java)

    // SceneViewContextToolbar actions.
    val sceneViewContextActions =
      (actions[5] as ShowGroupUnderConditionWrapper)
        .getChildren(null)
        .filterIsInstance<ShowUnderConditionWrapper>()
        .map { it.delegate as EnableUnderConditionWrapper }
        .map { it.delegate }

    assertThat(sceneViewContextActions.size)
      .isEqualTo(EXPECTED_SCENE_VIEW_TOOLBAR_NUMBER_OF_ACTIONS)
    assertThat((sceneViewContextActions[0] as AnActionWrapper).delegate)
      .isInstanceOf(SavePreviewInNewSizeAction::class.java)
    assertThat(sceneViewContextActions[1]).isInstanceOf(EnableUiCheckAction::class.java)
    assertThat(sceneViewContextActions[2]).isInstanceOf(AnimationInspectorAction::class.java)
    assertThat(sceneViewContextActions[3]).isInstanceOf(EnableInteractiveAction::class.java)
    assertThat(sceneViewContextActions[4]).isInstanceOf(DeployToDeviceAction::class.java)

    // The back navigation action is wrapped into the EnableUnderConditionWrapper and then into
    // the visibleOnlyInInteractive wrapper.
    val backNavigationAction =
      ((actions[6] as AnActionWrapper).delegate as AnActionWrapper).delegate
    assertThat(backNavigationAction).isInstanceOf(BackNavigationAction::class.java)

    // AI actions
    val aiActionsDefaultGroup =
      (actions[7] as ShowGroupUnderConditionWrapper).getChildren(null).single()
    assertThat(aiActionsDefaultGroup.templatePresentation.text)
      .isEqualTo(if (aiActionsDropdownEnabled) "previewAgents" else "transformPreview")
    assertThat(aiActionsDefaultGroup is DefaultActionGroup)
  }

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
        (actionManager
            .getSceneViewContextToolbarOverflowActions()
            .filterIsInstance<ActionGroup>()
            .single())
          .getChildren(testEvent)

      assertEquals(EXPECTED_SCENE_VIEW_TOOLBAR_NUMBER_OF_ACTIONS, actions.size)

      val visibleActions =
        actions.filter {
          it.update(testEvent)
          testEvent.presentation.isVisible
        }

      assertThat(visibleActions).hasSize(EXPECTED_SCENE_VIEW_TOOLBAR_VISIBLE_ACTIONS)

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
      (actionManager
          .getSceneViewContextToolbarOverflowActions()
          .filterIsInstance<ActionGroup>()
          .single())
        .getChildren(createTestEvent(dataContext))
    assertEquals(EXPECTED_SCENE_VIEW_TOOLBAR_NUMBER_OF_ACTIONS, actions.size)

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

      assertThat(visibleActions).hasSize(EXPECTED_SCENE_VIEW_TOOLBAR_VISIBLE_ACTIONS)

      visibleActions.forEach { action ->
        action.update(testEvent)
        assertEquals(true, testEvent.presentation.isEnabled)
        assertNotEquals("Project needs build", testEvent.presentation.description)
      }
    }
  }

  @Test
  fun `verify actions contain glasses dropdown action if flag is enabled`() {
    StudioFlags.COMPOSE_PREVIEW_AI_GLASSES_PREVIEW.overrideForTest(
      true,
      projectRule.testRootDisposable,
    )
    assertTrue(
      actionManager.sceneViewContextToolbarActions
        .filterIsInstance<GlassesBlendDropdownAction>()
        .isNotEmpty()
    )
  }

  @Test
  fun `verify actions doesn't contain glasses dropdown action if flag is disabled`() {
    StudioFlags.COMPOSE_PREVIEW_AI_GLASSES_PREVIEW.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    assertTrue(
      actionManager.sceneViewContextToolbarActions
        .filterIsInstance<GlassesBlendDropdownAction>()
        .isEmpty()
    )
  }

  private val fakeMouseEvent = MouseEvent(JPanel(), 0, 0L, 0, 0, 0, 1, true)
}

private data class Status(
  override val hasRenderErrors: Boolean,
  override val hasSyntaxErrors: Boolean,
  override val isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override val isRefreshing: Boolean,
  override val previewedFile: PsiFile? = null,
) : PreviewViewModelStatus
