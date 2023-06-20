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
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.ACTION_COLOR
import com.android.tools.idea.naveditor.scene.ConnectionDirection
import com.android.tools.idea.naveditor.scene.SELECTED_COLOR
import com.android.tools.idea.naveditor.scene.draw.verifyDrawAction
import com.android.tools.idea.naveditor.scene.draw.verifyDrawFragment
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHeader
import com.android.tools.idea.naveditor.scene.getDestinationDirection
import com.android.tools.idea.naveditor.scene.verifyScene
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.truth.Truth.assertThat
import java.awt.Color
import java.awt.event.MouseEvent.BUTTON1
import java.awt.geom.Rectangle2D

/**
 * Tests for action hit providers
 */
class ActionHitProviderTest : NavTestCase() {
  fun testSelect() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }

    val surface = model.surface as NavDesignSurface
    val view = NavView(surface, surface.sceneManager!!)
    whenever(surface.focusedSceneView).thenReturn(view)

    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    val component2 = scene.getSceneComponent("fragment2")!!

    component.setPosition(0, 0)
    component2.setPosition(500, 0)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(DisplayList(), 0, view)


    val guiInputHandler = surface.guiInputHandler
    guiInputHandler.startListening()

    LayoutTestUtilities.clickMouse(guiInputHandler, BUTTON1, 1, Coordinates.getSwingXDip(view, 300),
                                   Coordinates.getSwingYDip(view, component.centerY), 0)

    assertEquals(model.find("action1"), surface.selectionModel.primary)
    guiInputHandler.stopListening()
  }

  fun testHighlight() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!

    scene.getSceneComponent("fragment1")!!.setPosition(200, 20)
    scene.getSceneComponent("fragment2")!!.setPosition(20, 20)
    scene.sceneManager.layout(false)

    val navView = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    val context = SceneContext.get(navView)
    scene.layout(0, context)

    val verifier = { color: Color ->
      verifyScene(model.surface) { inOrder, g ->
        verifyDrawAction(inOrder, g, color)

        verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
        verifyDrawFragment(inOrder, g, Rectangle2D.Float(490f, 400f, 76.5f, 128f), 0.5)

        verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 11f), 0.5, "fragment2")
        verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
      }
    }
    verifier(ACTION_COLOR)

    model.surface.selectionModel.setSelection(listOf(model.find("action1")!!))
    verifier(SELECTED_COLOR)
  }

  fun testDirection() {
    //  |---------|
    //  |    2  3 |
    //  | 4  1    |
    //  |    5  6 |
    //  |---------|
    val rect1 = SwingRectangle(Rectangle2D.Float(500f, 500f, 153f, 256f))
    val rect2 = SwingRectangle(Rectangle2D.Float(500f, 0f, 153f, 256f))
    val rect3 = SwingRectangle(Rectangle2D.Float(1000f, 0f, 153f, 256f))
    val rect4 = SwingRectangle(Rectangle2D.Float(0f, 500f, 153f, 256f))
    val rect5 = SwingRectangle(Rectangle2D.Float(500f, 1000f, 153f, 256f))
    val rect6 = SwingRectangle(Rectangle2D.Float(1000f, 1000f, 153f, 256f))

    assertThat(getDestinationDirection(rect2, rect1)).isEqualTo(ConnectionDirection.TOP)
    assertThat(getDestinationDirection(rect3, rect1)).isEqualTo(ConnectionDirection.TOP)
    assertThat(getDestinationDirection(rect4, rect1)).isEqualTo(ConnectionDirection.LEFT)
    assertThat(getDestinationDirection(rect5, rect1)).isEqualTo(ConnectionDirection.BOTTOM)
    assertThat(getDestinationDirection(rect6, rect1)).isEqualTo(ConnectionDirection.BOTTOM)
  }
}

