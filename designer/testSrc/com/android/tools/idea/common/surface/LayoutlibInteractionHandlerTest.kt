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

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.fixtures.KeyEventBuilder
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.uibuilder.surface.PanInteraction
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.testFramework.assertInstanceOf
import org.junit.Test
import java.awt.Point
import java.awt.event.KeyEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class TestInteractableSurface(private val sceneView: SceneView? = null) : InteractableScenesSurface {
  var zoomCounter = 0
  var hoverCounter = 0
  override fun zoom(type: ZoomType): Boolean {
    zoomCounter++
    return true
  }

  override fun canZoomIn() = true
  override fun canZoomOut() = true
  override fun canZoomToFit() = true
  override fun canZoomToActual() = true

  override fun onHover(x: Int, y: Int) { hoverCounter++ }

  override var isPanning = false
  override val isPannable = true
  override var scrollPosition = Point(0, 0)
  override val scale: Double = 1.0
  override val screenScalingFactor: Double = 1.0

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
    val spaceKeyEvent = KeyEventBuilder(DesignSurfaceShortcut.PAN.keyCode, DesignSurfaceShortcut.PAN.keyCode.toChar()).build()
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
    val handler = LayoutlibInteractionHandler(TestInteractableSurface(TestSceneView(100, 100)))
    val aKeyEvent = KeyEventBuilder(KeyEvent.VK_A, 'a').build()
    val interaction = handler.keyPressedWithoutInteraction(aKeyEvent)
    assertInstanceOf<LayoutlibInteraction>(interaction)
  }

  @Test
  fun testLayoutlibInteractionWhenMousePressed() {
    val handler = LayoutlibInteractionHandler(TestInteractableSurface(TestSceneView(100, 100)))
    val interaction = handler.createInteractionOnPressed(10, 10, 0)
    assertInstanceOf<LayoutlibInteraction>(interaction)
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
