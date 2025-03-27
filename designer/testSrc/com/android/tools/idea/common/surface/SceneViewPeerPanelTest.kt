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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class SceneViewPeerPanelTest {
  @get:Rule val projectRule = ApplicationRule()
  @get:Rule val disposableRule: DisposableRule = DisposableRule()

  @Test
  fun `top panel is hidden when label is hidden and no actions`() {
    val sceneViewPeerPanel = createSceneViewPeerPanel(disposableRule.disposable, "")
    assertFalse(sceneViewPeerPanel.sceneViewTopPanel.isVisible)
  }

  @Test
  fun `top panel is visible when label is hidden and actions are present`() {
    val action =
      object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    val sceneViewPeerPanel =
      createSceneViewPeerPanel(disposableRule.disposable, "", toolbarActions = listOf(action))
    assertTrue(sceneViewPeerPanel.sceneViewTopPanel.isVisible)
  }

  @Test
  fun `top panel is visible when label is visible and no actions`() {
    val sceneViewPeerPanel =
      createSceneViewPeerPanel(disposableRule.disposable, "", isLabelPanelVisible = true)
    assertTrue(sceneViewPeerPanel.sceneViewTopPanel.isVisible)
  }
}

private fun createSceneView(parentDisposable: Disposable, modelName: String): SceneView {
  val model =
    Mockito.mock(NlModel::class.java).apply {
      Mockito.`when`(this.organizationGroup).then { null }
      Mockito.`when`(this.displaySettings).then {
        DisplaySettings().apply { setDisplayName(modelName) }
      }
    }
  val sceneManager =
    Mockito.mock(SceneManager::class.java).apply { Mockito.`when`(this.model).then { model } }
  Disposer.register(parentDisposable, sceneManager)
  return TestSceneView(100, 100, sceneManager)
}

private fun createSceneViewPeerPanel(
  parentDisposable: Disposable,
  name: String,
  isLabelPanelVisible: Boolean = false,
  toolbarActions: List<AnAction> = emptyList(),
): SceneViewPeerPanel {
  val testScope = CoroutineScope(EmptyCoroutineContext)
  val sceneView = createSceneView(parentDisposable, name)
  return SceneViewPeerPanel(
    scope = testScope,
    sceneView = sceneView,
    labelPanel =
      object : JPanel() {
        init {
          isVisible = isLabelPanelVisible
        }
      },
    statusIconAction = null,
    toolbarActions = toolbarActions,
    leftPanel = null,
    rightPanel = null,
    errorsPanel = null,
    isOrganizationEnabled = MutableStateFlow(true),
  )
}
