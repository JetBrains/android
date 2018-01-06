/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import java.awt.event.MouseEvent.BUTTON1

/**
 * Test to verify that components are selected when
 * mousePress event is received.
 */
class LevelTest : NavTestCase() {
  fun testLevels() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val surface = model.surface as NavDesignSurface
    val sceneView = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.currentSceneView).thenReturn(sceneView)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(sceneView)

    val scene = model.surface.scene!!
    scene.layout(0, SceneContext.get())

    val interactionManager = InteractionManager(surface)
    interactionManager.startListening()

    val component1 = scene.getSceneComponent("fragment1")!!
    val component2 = scene.getSceneComponent("fragment2")!!

    @NavCoordinate var x1 = component1.centerX
    @NavCoordinate val y1 = component1.centerY

    @NavCoordinate var x2 = component2.centerX
    @NavCoordinate val y2 = component2.centerY

    checkSelections(x1, y1, x2, y2, component1, component2, interactionManager, sceneView)

    x1 = component1.drawX + component1.drawWidth
    x2 = component2.drawX + component2.drawWidth

    checkSelections(x1, y1, x2, y2, component1, component2, interactionManager, sceneView)
    interactionManager.stopListening()
  }

  private fun checkSelections(@NavCoordinate x1: Int,
                              @NavCoordinate y1: Int,
                              @NavCoordinate x2: Int,
                              @NavCoordinate y2: Int,
                              component1: SceneComponent,
                              component2: SceneComponent,
                              interactionManager: InteractionManager,
                              sceneView: SceneView) {
    mouseDown(x1, y1, interactionManager, sceneView)
    checkLevel(component2, component1)
    mouseUp(x1, y1, interactionManager, sceneView)
    checkLevel(component2, component1)

    mouseDown(x2, y2, interactionManager, sceneView)
    checkLevel(component1, component2)
    mouseUp(x2, y2, interactionManager, sceneView)
    checkLevel(component1, component2)
  }

  private fun mouseDown(@NavCoordinate x: Int, @NavCoordinate y: Int, interactionManager: InteractionManager, sceneView: SceneView) {
    @SwingCoordinate val swingX = Coordinates.getSwingX(sceneView, x)
    @SwingCoordinate val swingY = Coordinates.getSwingY(sceneView, y)
    @SwingCoordinate val swingDrag = Coordinates.getSwingDimension(sceneView, 30)

    LayoutTestUtilities.pressMouse(interactionManager, BUTTON1, swingX, swingY, 0)

    LayoutTestUtilities.dragMouse(interactionManager, swingX, swingY, swingX + swingDrag, swingY + swingDrag, 0)
    sceneView.scene.layout(0, SceneContext.get())
    LayoutTestUtilities.dragMouse(interactionManager, swingX + swingDrag, swingY + swingDrag, swingX, swingY, 0)
    sceneView.scene.layout(0, SceneContext.get())
  }

  private fun mouseUp(@NavCoordinate x: Int, @NavCoordinate y: Int, interactionManager: InteractionManager, sceneView: SceneView) {
    LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, Coordinates.getSwingX(sceneView, x),
        Coordinates.getSwingY(sceneView, y), 0)
  }

  private fun checkLevel(lower: SceneComponent, higher: SceneComponent) {
    val level1 = getLevel(lower)
    val level2 = getLevel(higher)
    assertTrue(level2 > level1)
  }

  private fun getLevel(component: SceneComponent): Int {
    val decorator = component.decorator
    val displayList = DisplayList()
    decorator.buildList(displayList, 0, SceneContext.get(), component)

    val commands = displayList.commands
    assertEquals(1, commands.size)
    return commands[0].level
  }
}
