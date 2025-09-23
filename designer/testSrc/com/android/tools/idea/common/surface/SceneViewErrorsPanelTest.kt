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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueFixActionProvider
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SceneViewErrorsPanelTest {

  @get:Rule val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var fakeUi: FakeUi
  private lateinit var panelParent: JPanel

  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @Before
  fun setUp() {
    panelParent =
      JPanel().apply {
        layout = BorderLayout()
        size = Dimension(1000, 800)
      }
    fakeUi = FakeUi(panelParent, 1.0, true)
    fakeUi.root.validate()
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.clearOverride()
  }

  @Test
  fun testVisibilityIsControlledByConstructorParameter() {
    var panelStyle = SceneViewErrorsPanel.Style.SOLID
    val sceneViewErrorsPanel =
      SceneViewErrorsPanel(errorProvider = { emptyList() }, styleProvider = { panelStyle })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertTrue(sceneViewErrorsPanel.isVisible)
    panelStyle = SceneViewErrorsPanel.Style.HIDDEN
    assertFalse(sceneViewErrorsPanel.isVisible)
  }

  @Test
  fun testPanelComponents() {
    val sceneViewErrorsPanel = SceneViewErrorsPanel(errorProvider = { emptyList() })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)
    fakeUi.root.validate()

    assertNotNull(fakeUi.findComponent<JBLabel> { it.text.contains("Render problem") })
  }

  @Test
  fun testPreferredAndMinimumSizes() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.override(false)
    val sceneViewErrorsPanel = SceneViewErrorsPanel(errorProvider = { emptyList() })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertEquals(35, sceneViewErrorsPanel.minimumSize.height)
    assertEquals(150, sceneViewErrorsPanel.minimumSize.width)
    assertEquals(35, sceneViewErrorsPanel.preferredSize.height)
    assertEquals(150, sceneViewErrorsPanel.preferredSize.width)
  }

  @Test
  fun testPreferredAndMinimumSizesForAiFix() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.override(true)
    val sceneViewErrorsPanel = SceneViewErrorsPanel(errorProvider = { emptyList() })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertEquals(70, sceneViewErrorsPanel.minimumSize.height)
    assertEquals(150, sceneViewErrorsPanel.minimumSize.width)
    assertEquals(70, sceneViewErrorsPanel.preferredSize.height)
    assertEquals(150, sceneViewErrorsPanel.preferredSize.width)
  }

  @Test
  fun testAiFixButtonIsAvailable() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.override(true)
    val testAction =
      object : AnAction("Fix with AI") {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    val issueFixActionProvider =
      object : IssueFixActionProvider {
        override fun getFixes(issue: Issue): List<AnAction> {
          return listOf(testAction)
        }
      }
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName("com.android.tools.idea.designer.issueFixActionProvider"),
      listOf(issueFixActionProvider),
      disposableRule.disposable,
    )

    val sceneViewErrorsPanel =
      SceneViewErrorsPanel(errorProvider = { listOf(Throwable("Test error")) })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)
    fakeUi.root.validate()
    fakeUi.updateToolbars()

    val toolbar =
      (sceneViewErrorsPanel.layout as BorderLayout).getLayoutComponent(BorderLayout.SOUTH)
        as ActionToolbar
    assertNotNull(toolbar)
    assertTrue(toolbar.actions.any { it.templatePresentation.text == "Fix with AI" })
  }


  @Test
  fun testAiFixButtonIsNotAvailableWhenFlagIsOff() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.override(false)
    val testAction =
      object : AnAction("Fix with AI") {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    val issueFixActionProvider =
      object : IssueFixActionProvider {
        override fun getFixes(issue: Issue): List<AnAction> {
          return listOf(testAction)
        }
      }
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName("com.android.tools.idea.designer.issueFixActionProvider"),
      listOf(issueFixActionProvider),
      disposableRule.disposable,
    )

    val sceneViewErrorsPanel =
      SceneViewErrorsPanel(errorProvider = { listOf(Throwable("Test error")) })
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)
    fakeUi.root.validate()
    fakeUi.updateToolbars()

    val toolbar =
      (sceneViewErrorsPanel.layout as BorderLayout).getLayoutComponent(BorderLayout.SOUTH)
        as ActionToolbar
    assertNotNull(toolbar)
    assertFalse(toolbar.actions.any { it.templatePresentation.text == "Fix with AI" })
  }
}
