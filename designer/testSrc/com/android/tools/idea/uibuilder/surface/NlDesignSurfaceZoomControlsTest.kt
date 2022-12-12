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
package com.android.tools.idea.uibuilder.surface

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.editor.zoomActionPlace
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.nio.file.Paths
import javax.swing.JPanel

class NlDesignSurfaceZoomControlsTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val facet: AndroidFacet
    get() = androidProjectRule.module.androidFacet!!
  private lateinit var layout: PsiFile
  private lateinit var surface: DesignSurface<LayoutlibSceneManager>
  private lateinit var fakeUi: FakeUi

  @Before
  fun setup() {
    androidProjectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    RenderService.setForTesting(androidProjectRule.project, NoSecurityManagerRenderService(androidProjectRule.project))

    layout = androidProjectRule.fixture.addFileToProject(
      "res/layout/test.xml",
      //language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:background="#0000FF"
              android:padding="30dp"
              android:orientation="vertical">
              <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize='40sp'
                android:text="Hello world"/>
        </LinearLayout>
      """.trimIndent())
    val configuration = RenderTestUtil.getConfiguration(androidProjectRule.fixture.module, layout.virtualFile)
    surface = invokeAndWaitIfNeeded {
      NlDesignSurface.builder(androidProjectRule.project, androidProjectRule.fixture.testRootDisposable)
        .setZoomControlsPolicy(DesignSurface.ZoomControlsPolicy.VISIBLE)
        .build()
    }

    val model = NlModel.builder(facet, layout.virtualFile, configuration)
      .withParentDisposable(androidProjectRule.testRootDisposable)
      .withComponentRegistrar(NlComponentRegistrar)
      .build()

    surface.addAndRenderModel(model).join()

    // Verify successful render
    assertEquals(1, surface.sceneManagers.size)
    val renderResult = surface.sceneManagers.single().renderResult!!
    assertTrue("The render must be successful. It was: $renderResult", renderResult.renderResult.isSuccess)

    fakeUi = invokeAndWaitIfNeeded {
      val outerPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.customLine(JBColor.RED)
        add(surface, BorderLayout.CENTER)
        setBounds(0, 0, 1000, 1000)
      }

      FakeUi(outerPanel, 1.0, true).apply {
        updateToolbars()
        layoutAndDispatchEvents()
      }
    }
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
  }

  private fun getGoldenImagePath(testName: String) =
    Paths.get("${androidProjectRule.fixture.testDataPath}/zoomGoldenImages/$testName.png")

  private fun FakeUi.updateToolbardsAndFullRefresh() = invokeAndWaitIfNeeded {
    updateToolbars()
    layoutAndDispatchEvents()
    root.repaint()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testNlDesignSurfaceZoom() {
    val zoomActionsToolbar = fakeUi.findComponent<ActionToolbarImpl> { it.place == zoomActionPlace }!!
    val zoomInAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomInAction>()
      .single()
    val zoomOutAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomOutAction>()
      .single()
    val zoomToFitAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomToFitAction>()
      .single()

    val event = TestActionEvent { dataId -> surface.getData(dataId) }
    zoomToFitAction.actionPerformed(event)
    val zoomToFitScale = surface.scale

    // Verify zoom in
    run {
      val originalScale = surface.scale
      repeat(3) { zoomInAction.actionPerformed(event) }
      assertTrue(surface.scale > originalScale)
      fakeUi.updateToolbardsAndFullRefresh()
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomIn"), fakeUi.render(), 0.1, 1)
    }

    // Verify zoom to fit
    run {
      zoomToFitAction.actionPerformed(event)
      assertEquals(zoomToFitScale, surface.scale, 0.01)
      fakeUi.updateToolbardsAndFullRefresh()
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomFit"), fakeUi.render(), 0.1, 1)
    }

    // Verify zoom out
    run {
      val originalScale = surface.scale
      repeat(3) { zoomOutAction.actionPerformed(event) }
      assertTrue(surface.scale < originalScale)
      fakeUi.updateToolbardsAndFullRefresh()
      ImageDiffUtil.assertImageSimilar(getGoldenImagePath("zoomOut"), fakeUi.render(), 0.1, 1)
    }
  }

  @Test
  fun testZoomControlsKeyboardInteractions() {
    val zoomActionsToolbar = fakeUi.findComponent<ActionToolbarImpl> { it.place == zoomActionPlace }!!
    val zoomInAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomInAction>()
      .single()
    val zoomOutAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomOutAction>()
      .single()
    val zoomToFitAction = zoomActionsToolbar.actions
      .filterIsInstance<ZoomToFitAction>()
      .single()

    val zoomToFitScale = surface.scale

    // Delegate context for keyboard events. This ensures that, when actions update, they get the right data context to make the
    // decision about visibility and presentation.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider { surface.getData(it) }

    // Verify zoom in
    run {
      val originalScale = surface.scale
      val keyStroke = zoomInAction
        .shortcutSet
        .shortcuts
        .filterIsInstance<KeyboardShortcut>()
        .first()
        .firstKeyStroke
      repeat(3) {
        invokeAndWaitIfNeeded {
          IdeEventQueue.getInstance().keyEventDispatcher.dispatchKeyEvent(
            KeyEvent(surface, keyStroke.keyEventType,
                     EventQueue.getMostRecentEventTime(), keyStroke.modifiers, keyStroke.keyCode, keyStroke.keyChar)
          )
        }
      }
      assertTrue(surface.scale > originalScale)
    }

    // Verify zoom to fit
    run {
      val keyStroke = zoomToFitAction
        .shortcutSet
        .shortcuts
        .filterIsInstance<KeyboardShortcut>()
        .first()
        .firstKeyStroke
      invokeAndWaitIfNeeded {
        IdeEventQueue.getInstance().keyEventDispatcher.dispatchKeyEvent(
          KeyEvent(surface, keyStroke.keyEventType,
                   EventQueue.getMostRecentEventTime(), keyStroke.modifiers, keyStroke.keyCode, keyStroke.keyChar)
        )
      }
      assertEquals(zoomToFitScale, surface.scale, 0.01)
    }

    // Verify zoom out
    run {
      val originalScale = surface.scale
      val keyStroke = zoomOutAction
        .shortcutSet
        .shortcuts
        .filterIsInstance<KeyboardShortcut>()
        .first()
        .firstKeyStroke
      repeat(3) {
        invokeAndWaitIfNeeded {
          IdeEventQueue.getInstance().keyEventDispatcher.dispatchKeyEvent(
            KeyEvent(surface, keyStroke.keyEventType,
                     EventQueue.getMostRecentEventTime(), keyStroke.modifiers, keyStroke.keyCode, keyStroke.keyChar)
          )
        }
      }
      assertTrue(surface.scale < originalScale)
    }
  }
}