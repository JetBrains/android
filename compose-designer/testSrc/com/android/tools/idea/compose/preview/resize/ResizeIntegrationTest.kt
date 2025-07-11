/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the 'License' is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the 'License' for the specific language governing permissions and
 * limitations under the 'License'.
 */
package com.android.tools.idea.compose.preview.resize

import com.android.annotations.concurrency.UiThread
import com.android.flags.junit.FlagRule
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.configurations.deviceSizeDp
import com.android.tools.idea.common.fixtures.MouseEventBuilder
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.ComposePreviewView
import com.android.tools.idea.compose.preview.ComposePreviewViewImpl
import com.android.tools.idea.compose.preview.ComposePreviewViewProvider
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RuleChain
import com.intellij.ui.components.fields.IntegerField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.android.compose.ComposeProjectRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ResizeIntegrationTest {

  private val projectRule = ComposeProjectRule(AndroidProjectRule.withAndroidModel())
  private val popupRule = PopupRule()

  @get:Rule
  val rule = RuleChain(projectRule, popupRule, FlagRule(StudioFlags.COMPOSE_PREVIEW_RESIZING, true))

  private lateinit var previewRepresentation: ComposePreviewRepresentation
  private lateinit var surface: NlDesignSurface
  private lateinit var fakeUi: FakeUi
  private lateinit var previewView: ComposePreviewView

  @Before
  fun setUp() {
    // Create VisualLintService early to avoid it being created at the time of project disposal
    VisualLintService.getInstance(projectRule.project)
  }

  @Test
  fun `resize and save integration test`() = runTest {
    val newWidth = 555
    val newHeight = 888

    val testPsiFile =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Composable
        @Preview
        fun MyPreview() {}
        """
          .trimIndent(),
      )

    setupPreview(testPsiFile)
    openResizePanel()
    resize(newWidth, newHeight)

    val sceneManager = surface.sceneManagers.single()
    val configuration = sceneManager.model.configuration
    val (deviceWidth, deviceHeight) = configuration.deviceSizeDp()
    assertEquals(newWidth, deviceWidth)
    assertEquals(newHeight, deviceHeight)

    clickContextMenuItem("Save New Preview (${newWidth}dp x ${newHeight}dp)")

    val fileContent = testPsiFile.text
    assertEquals(
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(
            name = "${newWidth}dp x ${newHeight}dp",
            widthDp = $newWidth,
            heightDp = $newHeight
        )
        @Composable
        @Preview
        fun MyPreview() {}
      """
        .trimIndent(),
      fileContent,
    )
  }

  private suspend fun setupPreview(testPsiFile: PsiFile) {
    var localSurface: NlDesignSurface? = null
    var localPreviewView: ComposePreviewView? = null

    val viewProvider =
      ComposePreviewViewProvider {
        project,
        psiFilePointer,
        statusManager,
        dataProvider,
        surfaceBuilder,
        parentDisposable ->
        val view =
          ComposePreviewViewImpl(
            project,
            psiFilePointer,
            statusManager,
            dataProvider,
            surfaceBuilder,
            parentDisposable,
          )
        localPreviewView = view
        localSurface = view.mainSurface
        view
      }
    previewRepresentation =
      ComposePreviewRepresentation(testPsiFile, PreferredVisibility.SPLIT, viewProvider).also {
        Disposer.register(projectRule.testRootDisposable, it)
      }
    surface = localSurface!!
    previewView = localPreviewView!!

    val provider = EdtNoGetDataProvider { sink -> DataSink.uiDataSnapshot(sink, surface) }

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(
      provider,
      projectRule.testRootDisposable,
    )

    withContext(Dispatchers.EDT) {
      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(previewRepresentation.component, BorderLayout.CENTER)
          },
          1.0,
          true,
        )

      projectRule.buildSystemServices.simulateArtifactBuild(
        ProjectSystemBuildManager.BuildStatus.SUCCESS
      )
      previewRepresentation.onActivate()
      delayUntilCondition(200) {
        previewRepresentation
          .renderedPreviewElementsInstancesFlowForTest()
          .value
          .asCollection()
          .isNotEmpty()
      }

      previewView.updateVisibilityAndNotifications()
      fakeUi.layoutAndDispatchEvents()
    }
  }

  @UiThread
  private fun openContextMenu(): JPopupMenu? {
    val interactionPane = surface.interactionPane
    val guiInputHandler = surface.guiInputHandler
    val mouseEvent =
      MouseEventBuilder(interactionPane.width / 2, interactionPane.height / 2)
        .withSource(interactionPane)
        .withComponent(interactionPane)
        .withButton(MouseEvent.BUTTON3)
        .withId(MouseEvent.MOUSE_PRESSED)
        .withPopupTrigger()
        .build()
    (guiInputHandler.listener as MouseListener).mousePressed(mouseEvent)
    return popupRule.popupContents as? JPopupMenu
  }

  private suspend fun clickContextMenuItem(text: String) =
    withContext(Dispatchers.EDT) {
      val popupMenu = openContextMenu()
      assertNotNull("Popup menu not found", popupMenu)
      val actionMenuItem = popupMenu!!.findDescendant<ActionMenuItem> { it.text == text }
      assertNotNull("Action not found in popup", actionMenuItem)
      actionMenuItem!!.doClick()
      popupRule.mockPopup.hide()
      popupMenu.isVisible = false
    }

  private suspend fun openResizePanel() =
    withContext(Dispatchers.EDT) {
      // The ResizePanel is created when entering Focus mode, so we need to trigger that.
      val previewElement =
        previewRepresentation
          .renderedPreviewElementsInstancesFlowForTest()
          .value
          .asCollection()
          .first()

      previewRepresentation.setMode(PreviewMode.Focus(previewElement))
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
      fakeUi.layoutAndDispatchEvents()

      clickContextMenuItem("Show Resize Toolbar")

      val resizePanel = fakeUi.findComponent<ResizePanel>()
      assertNotNull("ResizePanel not found", resizePanel)

      @Suppress("DEPRECATION") fakeUi.updateToolbars()
      fakeUi.layoutAndDispatchEvents()
    }

  private suspend fun resize(newWidth: Int, newHeight: Int) =
    withContext(Dispatchers.EDT) {
      val (widthTextField, heightTextField) = fakeUi.findAllComponents<IntegerField>()

      widthTextField.value = newWidth
      heightTextField.value = newHeight // Lose focus to trigger the update
      widthTextField.focusListeners.forEach {
        it.focusLost(FocusEvent(widthTextField, FocusEvent.FOCUS_LOST))
      }
      heightTextField.focusListeners.forEach {
        it.focusLost(FocusEvent(heightTextField, FocusEvent.FOCUS_LOST))
      }
    }
}
