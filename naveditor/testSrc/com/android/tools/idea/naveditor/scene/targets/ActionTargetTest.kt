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
    `when`<SceneView>(surface.focusedSceneView).thenReturn(view)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(view)

    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    val component2 = scene.getSceneComponent("fragment2")!!

    component.setPosition(0, 0)
    component2.setPosition(500, 0)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(DisplayList(), 0, view)


    val interactionManager = surface.interactionManager
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
        }
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!

    scene.getSceneComponent("fragment1")!!.setPosition(200, 20)
    scene.getSceneComponent("fragment2")!!.setPosition(20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val navView = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    val context = SceneContext.get(navView)
    scene.layout(0, context)
    scene.buildDisplayList(list, 0, context)

    val displayListTemplate = "Clip,0,0,967,928\n" +
                              "DrawAction,490.0x400.0x76.5x128.0,400.0x400.0x76.5x128.0,0.5,%1\$s,false\n" +
                              "\n" +
                              "DrawHeader,490.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
                              "DrawFragment,490.0x400.0x76.5x128.0,0.5,null\n" +
                              "\n" +
                              "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment2,false,false\n" +
                              "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
                              "\n" +
                              "UNClip\n"

    assertEquals(displayListTemplate.format("b2a7a7a7"), list.generateSortedDisplayList())

    model.surface.selectionModel.setSelection(listOf(model.find("action1")))
    list.clear()
    scene.buildDisplayList(list, 0, context)

    assertEquals(displayListTemplate.format("ff1886f7"), list.generateSortedDisplayList())
  }

  fun testDirection() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1")
        fragment("fragment2") {
          action(id = "action1", destination = "fragment1")
        }
        fragment("fragment3") {
          action(id = "action2", destination = "fragment1")
        }
        fragment("fragment4") {
          action(id = "action3", destination = "fragment1")
        }
        fragment("fragment5") {
          action(id = "action4", destination = "fragment1")
        }
        fragment("fragment6") {
          action(id = "action5", destination = "fragment1")
        }
      }
    }

    val scene = model.surface.scene!!

    //  |---------|
    //  |    2  3 |
    //  | 4  1    |
    //  |    5  6 |
    //  |---------|
    scene.getSceneComponent("fragment1")!!.setPosition(500, 500)
    scene.getSceneComponent("fragment2")!!.setPosition(500, 0)
    scene.getSceneComponent("fragment3")!!.setPosition(1000, 0)
    scene.getSceneComponent("fragment4")!!.setPosition(0, 500)
    scene.getSceneComponent("fragment5")!!.setPosition(500, 1000)
    scene.getSceneComponent("fragment6")!!.setPosition(1000, 1000)

    scene.sceneManager.layout(false)
    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneView.context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    // Arrows should be down for 2 and 3, right for 4, up for 5 and 6
    assertEquals(
      "Clip,0,0,1377,1428\n" +
      "DrawAction,650.0x400.0x76.5x128.0,650.0x650.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,900.0x400.0x76.5x128.0,650.0x650.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,400.0x650.0x76.5x128.0,650.0x650.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,650.0x900.0x76.5x128.0,650.0x650.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,900.0x900.0x76.5x128.0,650.0x650.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,650.0x639.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,650.0x650.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,650.0x389.0x76.5x11.0,0.5,fragment2,false,false\n" +
      "DrawFragment,650.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,900.0x389.0x76.5x11.0,0.5,fragment3,false,false\n" +
      "DrawFragment,900.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,400.0x639.0x76.5x11.0,0.5,fragment4,false,false\n" +
      "DrawFragment,400.0x650.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,650.0x889.0x76.5x11.0,0.5,fragment5,false,false\n" +
      "DrawFragment,650.0x900.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,900.0x889.0x76.5x11.0,0.5,fragment6,false,false\n" +
      "DrawFragment,900.0x900.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testTooltips() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action ("a1", destination = "f2")
          action("self", destination = "f1")
        }
        fragment("f2")
      }
    }
    val scene = model.surface.scene!!
    val target1 = ActionTarget(scene.getSceneComponent("a1")!!, scene.getSceneComponent("f1")!!, scene.getSceneComponent("f2")!!)
    assertEquals("a1", target1.toolTipText)
    assertEquals("a1", target1.toolTipText)
    val target2 = ActionTarget(scene.getSceneComponent("self")!!, scene.getSceneComponent("f1")!!, scene.getSceneComponent("f1")!!)
    assertEquals("self", target2.toolTipText)
  }
}

