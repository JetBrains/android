package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.EnableUnderConditionWrapper
import com.android.tools.idea.common.util.ShowGroupUnderConditionWrapper
import com.android.tools.idea.common.util.ShowUnderConditionWrapper
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.AnimationInspectorAction
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
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// CopyResultImageAction()
// ZoomToSelectionAction(),
// JumpToDefinitionAction(),
// ViewInFocusModeAction(),
// ToggleResizePanelVisibilityAction(),
// Separator(),
// SavePreviewInNewSizeAction(),
// EnableUiCheckAction(),
// AnimationInspectorAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction(),
// TransformPreviewAction(),
// FixVisualLintIssuesAction(),
// AlignUiToTargetImageAction(),
// in wrappers
private const val EXPECTED_NUMBER_OF_ACTIONS = 10

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
    StudioFlags.COMPOSE_CRITIQUE_AGENT_CODE_REWRITE.clearOverride()
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.clearOverride()
  }

  @Test
  fun testAvailableActionsOnPreviewContextMenu() {
    StudioFlags.COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI.override(true)
    StudioFlags.COMPOSE_CRITIQUE_AGENT_CODE_REWRITE.override(true)
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.override(true)
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
    assertThat((actions[4] as AnActionWrapper).delegate)
      .isInstanceOf(ToggleResizePanelVisibilityAction::class.java)

    assertThat(actions[5]).isInstanceOf(Separator::class.java)

    // SceneViewContextToolbar actions.
    val sceneViewContextActions =
      (actions[6] as ShowGroupUnderConditionWrapper)
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

    // Transform Preview action.
    val transformPreviewAction =
      (actions[7] as ShowGroupUnderConditionWrapper).getChildren(null).single()
    assertThat(transformPreviewAction.templatePresentation.text).isEqualTo("transformPreview")

    // Fix Visual Lint Issues action.
    val fixVisualLintIssuesAction =
      (actions[8] as ShowGroupUnderConditionWrapper).getChildren(null).single()
    assertThat(fixVisualLintIssuesAction.templatePresentation.text).isEqualTo("fixVisualLintIssues")

    // Align Ui to Image action.
    val alignUiImageAction =
      (actions[9] as ShowGroupUnderConditionWrapper).getChildren(null).single()
    assertThat(alignUiImageAction.templatePresentation.text).isEqualTo("alignUi")
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
        (actionManager.getSceneViewContextToolbarActions().filterIsInstance<ActionGroup>().single())
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
      (actionManager.getSceneViewContextToolbarActions().filterIsInstance<ActionGroup>().single())
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

class FakeStudioBotActionFactory : ComposeStudioBotActionFactory {

  var isNullPreviewGeneratorAction = false

  private fun fakeAction(text: String): AnAction {
    return object : AnAction(text) {
      override fun actionPerformed(e: AnActionEvent) {}
    }
  }

  override fun createPreviewGenerator() =
    if (isNullPreviewGeneratorAction) null else fakeAction("previewGenerator")

  override fun transformPreviewAction() = fakeAction("transformPreview")

  override fun fixVisualLintIssuesAction(visualLintIssues: List<VisualLintRenderIssue>) =
    fakeAction("fixVisualLintIssues")

  override fun fixComposeRenderIssueAction(renderIssues: List<Issue>): AnAction? =
    fakeAction("fixComposeRender")

  override fun alignUiToTargetImageAction(): AnAction? = fakeAction("alignUi")

  override fun previewAgentsDropDownAction(): AnAction? = fakeAction("previewAgents")
}
