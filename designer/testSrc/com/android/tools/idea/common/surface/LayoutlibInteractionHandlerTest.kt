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

import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.DesignSurfaceTestUtil.createZoomControllerFake
import com.android.tools.idea.common.fixtures.KeyEventBuilder
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.android.tools.idea.uibuilder.surface.interaction.PanInteraction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.assertInstanceOf
import java.awt.Point
import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

private class TestInteractableSurface(private val sceneView: SceneView? = null) :
  InteractableScenesSurface {
  var zoomCounter = 0
  var hoverCounter = 0

  override fun onHover(x: Int, y: Int) {
    hoverCounter++
  }

  override val zoomController: ZoomController
    get() = createZoomControllerFake { zoomCounter++ }

  override var isPanning = false
  override val isPannable = true
  override var scrollPosition = Point(0, 0)

  override fun getData(dataId: String) = null

  override fun getSceneViewAtOrPrimary(x: Int, y: Int) = sceneView

  override val scene: Scene? = null
  override val focusedSceneView = sceneView

  override fun getSceneViewAt(x: Int, y: Int) = sceneView
}

class LayoutlibInteractionHandlerTest {

  @Test
  fun testStartPanningWhenPressingSpace() {
    val handler = LayoutlibInteractionHandler(TestInteractableSurface())
    val spaceKeyEvent =
      KeyEventBuilder(DesignSurfaceShortcut.PAN.keyCode, DesignSurfaceShortcut.PAN.keyCode.toChar())
        .build()
    val interaction = handler.keyPressedWithoutInteraction(spaceKeyEvent)
    assertInstanceOf<PanInteraction>(interaction)
  }

  @Test
  fun testNoInteractionWhenPressingNonSpaceKeyAndNoSceneView() {
    val handler = LayoutlibInteractionHandler(TestInteractableSurface())
    val aKeyEvent = KeyEventBuilder(KeyEvent.VK_A, 'a').build()
    assertNull(handler.keyPressedWithoutInteraction(aKeyEvent))
  }

  @Test
  fun testLayoutlibInteractionWhenPressingNonSpaceKeyAndSceneViewExists() {
    val sceneManager = Mockito.mock(SceneManager::class.java)
    val handler =
      LayoutlibInteractionHandler(TestInteractableSurface(TestSceneView(100, 100, sceneManager)))
    val aKeyEvent = KeyEventBuilder(KeyEvent.VK_A, 'a').build()
    val interaction = handler.keyPressedWithoutInteraction(aKeyEvent)
    assertInstanceOf<LayoutlibInteraction>(interaction)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun testLayoutlibInteractionWhenMousePressed() {
    val sceneManager = Mockito.mock(SceneManager::class.java)
    val handler =
      LayoutlibInteractionHandler(TestInteractableSurface(TestSceneView(100, 100, sceneManager)))
    val interaction = handler.createInteractionOnPressed(10, 10, 0)
    assertInstanceOf<LayoutlibInteraction>(interaction)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun testHoverIsPassedThrough() {
    val surface = TestInteractableSurface()
    val handler = LayoutlibInteractionHandler(surface)
    assertEquals(0, surface.hoverCounter)
    handler.stayHovering(10, 10)
    assertEquals(1, surface.hoverCounter)
  }

  @Test
  fun testZoomIsPassedThrough() {
    val surface = TestInteractableSurface()
    val handler = LayoutlibInteractionHandler(surface)
    assertEquals(0, surface.zoomCounter)
    handler.zoom(ZoomType.ACTUAL, 10, 10)
    assertEquals(1, surface.zoomCounter)
  }
}
