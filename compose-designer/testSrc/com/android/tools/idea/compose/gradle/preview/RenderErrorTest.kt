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
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class RenderErrorTest {

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  /** Activates the [ComposePreviewRepresentation] and waits for scenes to complete rendering. */
  @OptIn(ExperimentalTime::class)
  suspend fun ComposePreviewRepresentation.activateAndWaitForRender(fakeUi: FakeUi) =
    withTimeout(timeout = 30.seconds) {
      onActivate()

      val sceneViewPeerPanels = mutableSetOf<SceneViewPeerPanel>()
      while (isActive && sceneViewPeerPanels.isEmpty()) {
        withContext(Dispatchers.Main) {
          delay(250)
          invokeAndWaitIfNeeded { fakeUi.root.validate() }
          sceneViewPeerPanels.addAll(fakeUi.findAllComponents())
        }
      }

      // Now wait for them to be rendered
      while (isActive &&
        sceneViewPeerPanels.any {
          (it.sceneView.sceneManager as? LayoutlibSceneManager)?.renderResult == null
        }) {
        delay(250)
      }
    }

  @Test
  fun testSceneViewHasRenderErrors() {
    val mainFile =
      project.guessProjectDir()!!.findFileByRelativePath(
        SimpleComposeAppPaths.APP_RENDER_ERROR.path
      )!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }
    val composePreviewRepresentation: ComposePreviewRepresentation

    val previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    lateinit var fakeUi: FakeUi
    invokeAndWaitIfNeeded {
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
    composePreviewRepresentation.onActivate()

    runBlocking { composePreviewRepresentation.activateAndWaitForRender(fakeUi) }

    val panels = fakeUi.findAllComponents<SceneViewPeerPanel>()

    val sceneViewPanelWithErrors = panels.single { it.displayName == "PreviewWithRenderErrors" }
    assertTrue(sceneViewPanelWithErrors.sceneView.hasRenderErrors())
    val visibleErrorsPanel =
      TreeWalker(sceneViewPanelWithErrors)
        .descendants()
        .filterIsInstance<SceneViewErrorsPanel>()
        .single()
    assertTrue(visibleErrorsPanel.isVisible)

    val sceneViewPanelWithoutErrors =
      panels.single { it.displayName == "PreviewWithoutRenderErrors" }
    assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())
    val invisibleErrorsPanel =
      TreeWalker(sceneViewPanelWithoutErrors)
        .descendants()
        .filterIsInstance<SceneViewErrorsPanel>()
        .single()
    assertFalse(invisibleErrorsPanel.isVisible)
  }
}
