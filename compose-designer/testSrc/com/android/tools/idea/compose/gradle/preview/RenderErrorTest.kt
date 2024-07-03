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
package com.android.tools.idea.compose.gradle.preview

import com.android.flags.junit.FlagRule
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.SceneViewErrorsPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.gradle.waitForRender
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzerInspection
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.utils.alwaysTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RenderErrorTest {

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  @get:Rule val flagRule = FlagRule(StudioFlags.PREVIEW_KEEP_IMAGE_ON_ERROR)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val log = Logger.getInstance(RenderErrorTest::class.java)

  private lateinit var fakeUi: FakeUi
  private lateinit var composePreviewRepresentation: ComposePreviewRepresentation
  private lateinit var previewView: TestComposePreviewView

  private val panels: List<SceneViewPeerPanel>
    get() =
      fakeUi.findAllComponents<SceneViewPeerPanel>().also { panels ->
        panels.forEach { log.debug("Found SceneViewPeerPanel ${it.displayName}") }
        fakeUi.findAllComponents<SceneViewErrorsPanel>().forEach {
          log.debug("Found SceneViewErrorsPanel $it")
        }
      }

  @Before
  fun setup() {
    log.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    @Suppress("UnstableApiUsage")
    ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))

    val mainFile =
      project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_RENDER_ERROR.path)!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }

    previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }

    val visualLintInspections =
      arrayOf(
        ButtonSizeAnalyzerInspection(),
        LongTextAnalyzerInspection(),
        TextFieldSizeAnalyzerInspection(),
        AtfAnalyzerInspection(),
      )
    projectRule.fixture.enableInspections(*visualLintInspections)
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    runBlocking {
      fakeUi =
        withContext(uiThread) {
          FakeUi(
              JPanel().apply {
                layout = BorderLayout()
                size = Dimension(1000, 800)
                add(previewView, BorderLayout.CENTER)
              },
              1.0,
              true,
            )
            .also { it.root.validate() }
        }
      composePreviewRepresentation.activateAndWaitForRender(fakeUi, timeout = 1.minutes)
    }
  }

  @Test
  fun testSceneViewWithRenderErrors() =
    runBlocking(workerThread) {
      StudioFlags.PREVIEW_KEEP_IMAGE_ON_ERROR.override(true)
      startUiCheckForModel("PreviewWithRenderErrors")

      lateinit var sceneViewPanelWithErrors: SceneViewPeerPanel
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == "Medium Phone - PreviewWithRenderErrors" }
          ?.takeIf { it.sceneView.hasRenderErrors() }
          ?.also { sceneViewPanelWithErrors = it } != null
      }

      val visibleErrorsPanel =
        TreeWalker(sceneViewPanelWithErrors)
          .descendants()
          .filterIsInstance<SceneViewErrorsPanel>()
          .single()
      assertFalse(visibleErrorsPanel.isVisible)

      val actions = sceneViewPanelWithErrors.getToolbarActions()
      // 4 actions expected: ui check, animation, interactive and deploy to device
      assertEquals(4, actions.size)
      // The visible/invisible state before the update shouldn't affect the final result
      for (visibleBefore in listOf(true, false)) {
        // All actions should be invisible when there are render errors
        assertEquals(0, countVisibleActions(actions, visibleBefore, sceneViewPanelWithErrors))
      }
    }

  @Test
  fun testSceneViewWithRenderErrorsWithNoKeepImageOnError() =
    runBlocking(workerThread) {
      StudioFlags.PREVIEW_KEEP_IMAGE_ON_ERROR.override(false)
      startUiCheckForModel("PreviewWithRenderErrors")

      lateinit var sceneViewPanelWithErrors: SceneViewPeerPanel
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == "Medium Phone - PreviewWithRenderErrors" }
          ?.takeIf { it.sceneView.hasRenderErrors() }
          ?.also { sceneViewPanelWithErrors = it } != null
      }

      val visibleErrorsPanel =
        TreeWalker(sceneViewPanelWithErrors)
          .descendants()
          .filterIsInstance<SceneViewErrorsPanel>()
          .single()
      assertTrue(visibleErrorsPanel.isVisible)

      val actions = sceneViewPanelWithErrors.getToolbarActions()
      // 4 actions expected: ui check, animation, interactive and deploy to device
      assertEquals(4, actions.size)
      // The visible/invisible state before the update shouldn't affect the final result
      for (visibleBefore in listOf(true, false)) {
        // All actions should be invisible when there are render errors
        assertEquals(0, countVisibleActions(actions, visibleBefore, sceneViewPanelWithErrors))
      }
    }

  @Test
  fun testSceneViewWithoutRenderErrors() =
    runBlocking(workerThread) {
      startUiCheckForModel("PreviewWithoutRenderErrors")

      lateinit var sceneViewPanelWithoutErrors: SceneViewPeerPanel

      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == "Medium Phone - PreviewWithoutRenderErrors" }
          ?.also { sceneViewPanelWithoutErrors = it } != null
      }

      assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())

      val invisibleErrorsPanel =
        TreeWalker(sceneViewPanelWithoutErrors)
          .descendants()
          .filterIsInstance<SceneViewErrorsPanel>()
          .single()
      assertFalse(invisibleErrorsPanel.isVisible)

      val actions = sceneViewPanelWithoutErrors.getToolbarActions()
      // 4 actions expected: ui check mode, animation, interactive and deploy to device
      assertEquals(4, actions.size)
      // The visible/invisible state before the update shouldn't affect the final result
      for (visibleBefore in listOf(true, false)) {
        // The animation preview action shouldn't be visible because the preview being used doesn't
        // contain animations, but the interactive, ui check and deploy to device actions should be
        // visible as there are no render errors.
        val visibleActionCount = if (StudioFlags.COMPOSE_UI_CHECK_MODE.get()) 3 else 2
        assertEquals(
          visibleActionCount,
          countVisibleActions(actions, visibleBefore, sceneViewPanelWithoutErrors),
        )
      }
    }

  @Test
  fun testAtfErrors() =
    runBlocking(workerThread) {
      startUiCheckForModel("PreviewWithContrastError")

      val accessibilityIssue = accessibilityIssues().last()
      assertEquals("Insufficient text color contrast ratio", accessibilityIssue.summary)
      val navigatable = accessibilityIssue.components[0].navigatable
      assertTrue(navigatable is OpenFileDescriptor)
      assertEquals(1667, (navigatable as OpenFileDescriptor).offset)
      assertEquals("RenderError.kt", navigatable.file.name)
    }

  @Test
  fun testAtfErrorsOnSecondModel() =
    runBlocking(workerThread) {
      startUiCheckForModel("PreviewWithContrastErrorAgain")

      val accessibilityIssue = accessibilityIssues().last()
      assertEquals("Insufficient text color contrast ratio", accessibilityIssue.summary)
      val navigatable = accessibilityIssue.components[0].navigatable
      assertTrue(navigatable is OpenFileDescriptor)
      assertEquals(1817, (navigatable as OpenFileDescriptor).offset)
      assertEquals("RenderError.kt", navigatable.file.name)
    }

  private suspend fun runVisualLintErrorsForModel(modelWithIssues: String) {
    startUiCheckForModel(modelWithIssues)

    val issues = visualLintRenderIssues()
    // 1-2% of the time we get two issues instead of one. Only one of the issues has a
    // component
    // field that is populated. We attempt to retrieve it here.
    val issue = runInEdtAndGet {
      issues.first { it.components.firstOrNull()?.navigatable is OpenFileDescriptor }
    }

    assertEquals("Visual Lint Issue", issue.category)
    val navigatable = issue.components[0].navigatable
    assertTrue(navigatable is OpenFileDescriptor)
    assertEquals("RenderError.kt", (navigatable as OpenFileDescriptor).file.name)

    stopUiCheck()
  }

  @Test
  fun testVisualLintErrorsForPreviewWithContrastError() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithContrastError")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithContrastErrorAgain() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithContrastErrorAgain")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithWideButton() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithWideButton")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithLongText() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithLongText")
  }

  private fun countVisibleActions(
    actions: List<AnAction>,
    visibleBefore: Boolean,
    sceneViewPeerPanel: SceneViewPeerPanel,
  ): Int {
    var visibleAfterCount = 0
    val dataContext = DataContext { sceneViewPeerPanel.getData(it) }
    for (action in actions) {
      val event = createTestEvent(dataContext)
      event.presentation.isVisible = visibleBefore
      action.update(event)
      if (event.presentation.isVisible) visibleAfterCount++
    }
    return visibleAfterCount
  }

  private fun SceneViewPeerPanel.getToolbarActions(): List<AnAction> =
    sceneViewTopPanel.components
      .filterIsInstance<ActionToolbarImpl>()
      .single()
      .actions
      .filterIsInstance<DefaultActionGroup>()
      .single()
      .childActionsOrStubs
      .toList()

  private suspend fun startUiCheckForModel(model: String) {
    lateinit var uiCheckElement: ComposePreviewElementInstance<*>

    delayUntilCondition(250, timeout = 1.minutes) {
      previewView.mainSurface.models
        .firstOrNull { it.modelDisplayName.value == model }
        ?.dataContext
        ?.previewElement()
        ?.also { uiCheckElement = it } != null
    }

    val onRefreshCompletable = previewView.getOnRefreshCompletable()

    composePreviewRepresentation.setMode(
      PreviewMode.UiCheck(
        baseInstance = UiCheckInstance(uiCheckElement, isWearPreview = false),
        atfChecksEnabled = true,
        visualLintingEnabled = true,
      )
    )
    onRefreshCompletable.join()

    // Once we enable Ui Check we need to render again since we are now showing the selected preview
    // with the different analyzers of Ui Check (for example screen sizes, colorblind check etc).
    withContext(uiThread) {
      waitForRender(fakeUi.findAllComponents<SceneViewPeerPanel>().toSet(), timeout = 2.minutes)
      fakeUi.root.validate()
    }
  }

  private suspend fun stopUiCheck() {
    val onRefreshCompletable = previewView.getOnRefreshCompletable()
    composePreviewRepresentation.setMode(PreviewMode.Default())
    onRefreshCompletable.join()
    waitForAllRefreshesToFinish(1.minutes)
  }

  private suspend fun visualLintRenderIssues(
    filter: (VisualLintRenderIssue) -> Boolean = alwaysTrue()
  ): List<VisualLintRenderIssue> {
    val issueModel = VisualLintService.getInstance(project).issueModel
    var issues = emptyList<VisualLintRenderIssue>()
    delayUntilCondition(delayPerIterationMs = 300, timeout = 1.minutes) {
      issues = issueModel.issues.filterIsInstance<VisualLintRenderIssue>().filter(filter)
      issues.any { it.components.isNotEmpty() }
    }
    return issues
  }

  private suspend fun accessibilityIssues() = visualLintRenderIssues { it.type.isAtfErrorType() }
}
