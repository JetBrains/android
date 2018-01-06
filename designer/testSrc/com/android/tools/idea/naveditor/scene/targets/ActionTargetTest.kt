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
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import java.awt.event.MouseEvent.BUTTON1

/**
 * Tests for [ActionTarget]
 */
class ActionTargetTest : NavTestCase() {
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
    `when`<SceneView>(surface.currentSceneView).thenReturn(view)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(view)

    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    val component2 = scene.getSceneComponent("fragment2")!!

    component.setPosition(0, 0)
    component2.setPosition(500, 0)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(DisplayList(), 0, view)


    val interactionManager = InteractionManager(surface)
    interactionManager.startListening()

    LayoutTestUtilities.clickMouse(interactionManager, BUTTON1, 1, Coordinates.getSwingXDip(view, 300),
        Coordinates.getSwingYDip(view, component.centerY), 0)

    assertEquals(model.find("action1"), surface.selectionModel.primary)
    interactionManager.stopListening()
  }

  fun testHighlight() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
          fragment("fragment2")
        }
      }
    }

    val scene = model.surface.scene!!

    val list = DisplayList()
    val fragment1 = scene.getSceneComponent("fragment1")!!
    val navView = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    val context = SceneContext.get(navView)
    fragment1.buildDisplayList(0, list, context)

    assertEquals("DrawFilledRectangle,491x401x74x126,fffafafa,0\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawAction,NORMAL,390x390x0x0,390x390x0x0,NORMAL\n" +
        "DrawArrow,2,RIGHT,380x387x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,491x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,398x391x-8x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,390x390x7x7,START_DESTINATION\n" +
        "\n", list.generateSortedDisplayList(context))

    (fragment1.targets[fragment1.findTarget(ActionTarget::class.java)] as ActionTarget).isHighlighted = true
    list.clear()
    fragment1.buildDisplayList(0, list, context)

    assertEquals("DrawFilledRectangle,491x401x74x126,fffafafa,0\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawAction,NORMAL,390x390x0x0,390x390x0x0,HOVER\n" +
        "DrawArrow,2,RIGHT,380x387x5x6,ffa7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,491x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,398x391x-8x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,390x390x7x7,START_DESTINATION\n" +
        "\n", list.generateSortedDisplayList(context))
  }
}
