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

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
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
import java.awt.event.InputEvent
import java.awt.event.MouseEvent.BUTTON1

/**
 * Test to verify that components are selected when
 * mousePress event is received.
 */
class SelectionTest : NavTestCase() {

  fun testSelection() {
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

    @NavCoordinate var x1 = component1.drawX + component1.drawWidth / 2
    @NavCoordinate val y1 = component1.drawY + component1.drawHeight / 2
    @NavCoordinate var x2 = component2.drawX + component2.drawWidth / 2
    @NavCoordinate val y2 = component2.drawY + component2.drawHeight / 2

    checkSelection(x1, y1, x2, y2, component1, component2, true, interactionManager, sceneView)

    x1 = component1.drawX + component1.drawWidth
    x2 = component2.drawX + component2.drawWidth

    // clicking on the action handle selects the underlying destination
    // and clears all other selection
    checkSelection(x1, y1, x2, y2, component1, component2, false, interactionManager, sceneView)

    interactionManager.stopListening()
  }

  private fun checkSelection(@NavCoordinate x1: Int,
                             @NavCoordinate y1: Int,
                             @NavCoordinate x2: Int,
                             @NavCoordinate y2: Int,
                             component1: SceneComponent,
                             component2: SceneComponent,
                             allowsMultiSelect: Boolean,
                             interactionManager: InteractionManager,
                             sceneView: SceneView) {
    select(x1, y1, false, interactionManager, sceneView)
    assertTrue(component1.isSelected)
    assertFalse(component2.isSelected)

    select(x2, y2, false, interactionManager, sceneView)
    assertFalse(component1.isSelected)
    assertTrue(component2.isSelected)

    select(x1, y1, true, interactionManager, sceneView)
    assertTrue(component1.isSelected)
    assertEquals(component2.isSelected, allowsMultiSelect)

    select(x1, y1, true, interactionManager, sceneView)
    assertEquals(component1.isSelected, !allowsMultiSelect)
    assertEquals(component2.isSelected, allowsMultiSelect)
  }

  private fun select(@NavCoordinate x: Int, @NavCoordinate y: Int, shiftKey: Boolean, interactionManager: InteractionManager,
                     sceneView: SceneView) {
    val modifiers = if (shiftKey) InputEvent.SHIFT_DOWN_MASK else 0

    val swingX = Coordinates.getSwingX(sceneView, x)
    val swingY = Coordinates.getSwingX(sceneView, y)

    LayoutTestUtilities.moveMouse(interactionManager, 0, 0, swingX, swingY)
    LayoutTestUtilities.pressMouse(interactionManager, BUTTON1, swingX, swingY, modifiers)
    LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, swingX, swingY, modifiers)
  }
}
