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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.preview

import com.android.SdkConstants
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.PreviewNavigatableWrapper
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.preview.config.MutableDeviceConfig
import com.android.tools.preview.config.createDeviceInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.pom.Navigatable
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Cursor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.MenuSelectionManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavigatingInteractionHandlerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  lateinit var surface: NlDesignSurface
  lateinit var previewModeManager: PreviewModeManager

  @Before
  fun setUp() {
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }

    model.configuration.setDevice(MutableDeviceConfig().createDeviceInstance(), false)

    model.dataProvider =
      object : NlDataProvider(PreviewModeManager.KEY) {
        override fun getData(dataId: String): Any? = previewModeManager.takeIf { dataId == PreviewModeManager.KEY.name }
      }
    surface = NlSurfaceBuilder.builder(projectRule.project, projectRule.testRootDisposable).build()
    surface.addModelsWithoutRender(listOf(model))
    previewModeManager = CommonPreviewModeManager()
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      MenuSelectionManager.defaultManager().clearSelectedPath()
    }
  }

  @Test
  fun testResizingEnabledOnlyForNormalModes() {
    val handler = NavigatingInteractionHandler(surface, mock(), true)

    val screenView = surface.sceneManagers.single().sceneViews.first() as ScreenView

    // the screen is in the right top corner
    assertThat(screenView.x).isEqualTo(0)
    assertThat(screenView.y).isEqualTo(0)

    val size = screenView.scaledContentSize

    val mouseX = Coordinates.getSwingXDip(screenView, size.width + 1)
    val mouseY = Coordinates.getSwingYDip(screenView, size.height + 1)
    val modifiersEx = 0
    handler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx)

    previewModeManager.setMode(PreviewMode.Focus(mock()))

    assertThat(handler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx))
      .isEqualTo(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR))

    previewModeManager.setMode(PreviewMode.Interactive(mock()))

    assertThat(handler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)).isEqualTo(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
  }

  @Test
  fun testArrowKeyNavigationRequestsFocus() {
    val model1 = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout1.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }
    val model2 = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout2.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }
    surface.addModelsWithoutRender(listOf(model1, model2))

    val sceneView1 = surface.sceneManagers.first { it.model == model1 }.sceneViews.first()
    val sceneView2 = surface.sceneManagers.first { it.model == model2 }.sceneViews.first()

    surface.sceneManagers.first { it.model == model1 }.sceneViews.forEach { it.setLocation(0, 0) }
    surface.sceneManagers.first { it.model == model2 }.sceneViews.forEach { it.setLocation(100, 0) }
    // Move other scene views out of the way
    surface.sceneManagers
      .filter { it.model != model1 && it.model != model2 }
      .forEach { it.sceneViews.forEach { sv -> sv.setLocation(0, 200) } }

    val handler = NavigatingInteractionHandler(surface, mock(), true)

    // Select first component in first sceneView
    val component1 = sceneView1.firstComponent!!
    sceneView1.selectionModel.setSelection(listOf(component1))

    // Create a right arrow key event
    val keyEvent =
      KeyEvent(surface.interactionPane, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED)

    // This should move selection and request focus on the second preview panel.
    handler.keyPressedWithoutInteraction(keyEvent)

    assertThat(sceneView2.selectionModel.isSelected(sceneView2.firstComponent!!)).isTrue()
  }

  @Test
  fun testOptionClickShowsMenuWithElementsUnderClick() {
    runInEdtAndWait {
      FakeUi(surface, createFakeWindow = true, parentDisposable = projectRule.testRootDisposable)
    }

    runBlocking {
      val navigationHandler = mock<NavigationHandler>()
      val navigatable1 = mock<Navigatable>()
      val navigatable2 = mock<Navigatable>()
      val wrapper1 = PreviewNavigatableWrapper("Text, MainActivity.kt: 50", navigatable1)
      val wrapper2 = PreviewNavigatableWrapper("Column, MainActivity.kt: 48", navigatable2)

      val screenView = surface.sceneManagers.single().sceneViews.first() as ScreenView
      whenever(navigationHandler.findNavigatablesWithCoordinates(eq(screenView), any(), any(), eq(false), eq(true)))
        .thenReturn(listOf(wrapper1, wrapper2))

      val popupCreatedDeferred = CompletableDeferred<ActionPopupMenu>()
      val popupMenuListener =
        object : ActionPopupMenuListener {
          override fun actionPopupMenuCreated(menu: ActionPopupMenu) {
            if (menu.place == "Navigatables") {
              popupCreatedDeferred.complete(menu)
            }
          }
        }
      (ActionManager.getInstance() as ActionManagerEx).addActionPopupMenuListener(popupMenuListener, projectRule.testRootDisposable)

      val handler = NavigatingInteractionHandler(surface, navigationHandler, true)
      val mouseEvent =
        MouseEvent(
          surface.interactionPane,
          MouseEvent.MOUSE_CLICKED,
          System.currentTimeMillis(),
          InputEvent.ALT_DOWN_MASK,
          screenView.x + 10,
          screenView.y + 10,
          1,
          false,
        )

      handler.singleClick(mouseEvent, InputEvent.ALT_DOWN_MASK)

      val popupMenu = withTimeout(5.seconds) { popupCreatedDeferred.await() }
      assertThat(popupMenu.place).isEqualTo("Navigatables")

      val actions = popupMenu.actionGroup.getChildren(null)
      assertThat(actions).hasLength(2)
      assertThat(actions[0].templateText).isEqualTo("Text, MainActivity.kt: 50")
      assertThat(actions[1].templateText).isEqualTo("Column, MainActivity.kt: 48")

      // Verify selecting an action invokes navigateTo with the selected navigatable
      val actionEvent = TestActionEvent.createTestEvent(actions[0])
      actions[0].actionPerformed(actionEvent)

      waitForCondition(5.seconds) {
        Mockito.mockingDetails(navigationHandler).invocations.any {
          it.method.name == "navigateTo" && it.arguments.contains(navigatable1)
        }
      }
      verify(navigationHandler, never()).navigateTo(eq(screenView), eq(navigatable2), any())

      runInEdtAndWait {
        popupMenu.component.isVisible = false
        MenuSelectionManager.defaultManager().clearSelectedPath()
      }
    }
  }

  @Test
  fun testOptionClickWithNoNavigatablesDoesNotShowPopup() {
    runInEdtAndWait {
      FakeUi(surface, createFakeWindow = true, parentDisposable = projectRule.testRootDisposable)
    }

    runBlocking {
      val navigationHandler = mock<NavigationHandler>()
      val screenView = surface.sceneManagers.single().sceneViews.first() as ScreenView
      whenever(navigationHandler.findNavigatablesWithCoordinates(eq(screenView), any(), any(), eq(false), eq(true))).thenReturn(emptyList())

      val popupCreatedDeferred = CompletableDeferred<ActionPopupMenu>()
      val popupMenuListener =
        object : ActionPopupMenuListener {
          override fun actionPopupMenuCreated(menu: ActionPopupMenu) {
            if (menu.place == "Navigatables") {
              popupCreatedDeferred.complete(menu)
            }
          }
        }
      (ActionManager.getInstance() as ActionManagerEx).addActionPopupMenuListener(popupMenuListener, projectRule.testRootDisposable)

      val handler = NavigatingInteractionHandler(surface, navigationHandler, true)
      val mouseEvent =
        MouseEvent(
          surface.interactionPane,
          MouseEvent.MOUSE_CLICKED,
          System.currentTimeMillis(),
          InputEvent.ALT_DOWN_MASK,
          screenView.x + 10,
          screenView.y + 10,
          1,
          false,
        )

      handler.singleClick(mouseEvent, InputEvent.ALT_DOWN_MASK)

      delay(200.milliseconds)
      assertThat(popupCreatedDeferred.isCompleted).isFalse()
    }
  }

  @Test
  fun testNormalClickDoesNotShowPopupAndNavigatesDirectly() {
    runInEdtAndWait {
      FakeUi(surface, createFakeWindow = true, parentDisposable = projectRule.testRootDisposable)
    }

    runBlocking {
      val navigationHandler = mock<NavigationHandler>()
      val navigatable = mock<Navigatable>()
      val wrapper = PreviewNavigatableWrapper("Text, MainActivity.kt: 50", navigatable)

      val screenView = surface.sceneManagers.single().sceneViews.first() as ScreenView
      whenever(navigationHandler.findNavigatablesWithCoordinates(eq(screenView), any(), any(), eq(false), eq(false)))
        .thenReturn(listOf(wrapper))
      whenever(navigationHandler.navigateTo(eq(screenView), eq(navigatable), eq(false))).thenReturn(true)

      val popupCreatedDeferred = CompletableDeferred<ActionPopupMenu>()
      val popupMenuListener =
        object : ActionPopupMenuListener {
          override fun actionPopupMenuCreated(menu: ActionPopupMenu) {
            if (menu.place == "Navigatables") {
              popupCreatedDeferred.complete(menu)
            }
          }
        }
      (ActionManager.getInstance() as ActionManagerEx).addActionPopupMenuListener(popupMenuListener, projectRule.testRootDisposable)

      val handler = NavigatingInteractionHandler(surface, navigationHandler, true)
      val mouseEvent =
        MouseEvent(
          surface.interactionPane,
          MouseEvent.MOUSE_CLICKED,
          System.currentTimeMillis(),
          0,
          screenView.x + 10,
          screenView.y + 10,
          1,
          false,
        )

      handler.singleClick(mouseEvent, 0)

      waitForCondition(5.seconds) {
        Mockito.mockingDetails(navigationHandler).invocations.any {
          it.method.name == "navigateTo"
        }
      }
      verify(navigationHandler).navigateTo(eq(screenView), eq(navigatable), eq(false))
      assertThat(popupCreatedDeferred.isCompleted).isFalse()
    }
  }
}
