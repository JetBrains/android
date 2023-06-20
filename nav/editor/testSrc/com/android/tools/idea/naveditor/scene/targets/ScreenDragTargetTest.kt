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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import java.awt.event.MouseEvent.BUTTON1

/**
 * Tests for [ScreenDragTarget]
 */
class ScreenDragTargetTest : NavTestCase() {

  fun testMove() {
    val model = model("nav.xml") {
      navigation(startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }
    }
    val surface = model.surface as NavDesignSurface
    val view = NavView(surface, surface.sceneManager!!)
    whenever(surface.focusedSceneView).thenReturn(view)

    val scene = model.surface.scene!!
    scene.layout(0, SceneContext.get())

    val component = scene.getSceneComponent("fragment1")!!
    val guiInputHandler = surface.guiInputHandler
    try {
      guiInputHandler.startListening()

      @NavCoordinate val x = component.drawX
      @NavCoordinate val y = component.drawY

      LayoutTestUtilities.pressMouse(guiInputHandler, BUTTON1, Coordinates.getSwingX(view, x + 10),
                                     Coordinates.getSwingY(view, y + 10), 0)
      LayoutTestUtilities.dragMouse(guiInputHandler, Coordinates.getSwingX(view, x), Coordinates.getSwingY(view, y),
                                    Coordinates.getSwingX(view, x + 30), Coordinates.getSwingY(view, y + 40), 0)
      LayoutTestUtilities.releaseMouse(guiInputHandler, BUTTON1, Coordinates.getSwingX(view, x + 30),
                                       Coordinates.getSwingY(view, y + 40), 0)

      assertEquals(x + 20, component.drawX)
      assertEquals(y + 30, component.drawY)
    }
    finally {
      guiInputHandler.stopListening()
    }
  }
}
