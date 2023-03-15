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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.SceneViewErrorsPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class RenderErrorTest {

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val log = Logger.getInstance(RenderErrorTest::class.java)
  private lateinit var panels: List<SceneViewPeerPanel>

  @Before
  fun setup() {
    log.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)

    val mainFile =
      project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_RENDER_ERROR.path)!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }
    val composePreviewRepresentation: ComposePreviewRepresentation

    val previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    lateinit var fakeUi: FakeUi
    UIUtil.invokeAndWaitIfNeeded(
      Runnable {
        fakeUi =
          FakeUi(
            JPanel().apply {
              layout = BorderLayout()
              size = Dimension(1000, 800)
              add(previewView, BorderLayout.CENTER)
            },
            1.0,
            true
          )
        fakeUi.root.validate()
      }
    )

    runBlocking { composePreviewRepresentation.activateAndWaitForRender(fakeUi) }

    panels = fakeUi.findAllComponents<SceneViewPeerPanel>()

    panels.forEach { log.debug("Found SceneViewPeerPanel ${it.displayName}") }
    fakeUi.findAllComponents<SceneViewErrorsPanel>().forEach {
      log.debug("Found SceneViewErrorsPanel $it")
    }
  }

  @Test
  fun testSceneViewWithRenderErrors() {
    val sceneViewPanelWithErrors = panels.single { it.displayName == "PreviewWithRenderErrors" }
    assertTrue(sceneViewPanelWithErrors.sceneView.hasRenderErrors())

    val visibleErrorsPanel =
      TreeWalker(sceneViewPanelWithErrors)
        .descendants()
        .filterIsInstance<SceneViewErrorsPanel>()
        .single()
    assertTrue(visibleErrorsPanel.isVisible)

    val actions = sceneViewPanelWithErrors.getToolbarActions()
    // 3 actions expected: animation, interactive and deploy to device
    assertEquals(3, actions.size)
    // The visible/invisible state before the update shouldn't affect the final result
    for (visibleBefore in listOf(true, false)) {
      // All actions should be invisible when there are render errors
      assertEquals(0, countVisibleActions(actions, visibleBefore))
    }
  }

  @Ignore("b/272048512")
  @Test
  fun testSceneViewWithoutRenderErrors() {
    val sceneViewPanelWithoutErrors =
      panels.single { it.displayName == "PreviewWithoutRenderErrors" }
    assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())

    val invisibleErrorsPanel =
      TreeWalker(sceneViewPanelWithoutErrors)
        .descendants()
        .filterIsInstance<SceneViewErrorsPanel>()
        .single()
    assertFalse(invisibleErrorsPanel.isVisible)

    val actions = sceneViewPanelWithoutErrors.getToolbarActions()
    // 3 actions expected: animation, interactive and deploy to device
    assertEquals(3, actions.size)
    // The visible/invisible state before the update shouldn't affect the final result
    for (visibleBefore in listOf(true, false)) {
      // The animation preview action shouldn't be visible because the preview being used doesn't
      // contain animations, but the interactive and deploy to device actions should be visible as
      // there are no render errors.
      assertEquals(2, countVisibleActions(actions, visibleBefore))
    }
  }

  private fun countVisibleActions(actions: List<AnAction>, visibleBefore: Boolean): Int {
    var visibleAfterCount = 0
    for (action in actions) {
      val event = TestActionEvent()
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
}
