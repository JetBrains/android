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
    scene.layout(0, context)
    fragment1.buildDisplayList(0, list, context)

    assertEquals("DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawPreviewUnavailable,491x401x74x126\n" +
        "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,UP,435x532x6x5,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,498x390x68x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,490x389x7x7,START_DESTINATION\n" +
        "\n", list.generateSortedDisplayList(context))

    (fragment1.targets[fragment1.findTarget(ActionTarget::class.java)] as ActionTarget).isHighlighted = true
    list.clear()
    fragment1.buildDisplayList(0, list, context)

    assertEquals("DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawPreviewUnavailable,491x401x74x126\n" +
        "DrawAction,NORMAL,490x400x76x128,400x400x76x128,HOVER\n" +
        "DrawArrow,2,UP,435x532x6x5,ffa7a7a7\n" +
        "DrawTruncatedText,3,fragment1,498x390x68x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,490x389x7x7,START_DESTINATION\n" +
        "\n", list.generateSortedDisplayList(context))
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

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    // Arrows should be down for 2 and 3, right for 4, up for 5 and 6
    assertEquals(
        "Clip,0,0,1056,1198\n" +
            "DrawRectangle,1,640x640x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,641x641x74x126\n" +
            "DrawIcon,640x629x7x7,START_DESTINATION\n" +
            "DrawTruncatedText,3,fragment1,648x630x68x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "DrawRectangle,1,640x390x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,641x391x74x126\n" +
            "DrawAction,NORMAL,640x390x76x128,640x640x76x128,NORMAL\n" +
            "DrawArrow,2,DOWN,675x631x6x5,b2a7a7a7\n" +
            "DrawTruncatedText,3,fragment2,640x380x76x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "DrawRectangle,1,890x390x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,891x391x74x126\n" +
            "DrawAction,NORMAL,890x390x76x128,640x640x76x128,NORMAL\n" +
            "DrawArrow,2,DOWN,675x631x6x5,b2a7a7a7\n" +
            "DrawTruncatedText,3,fragment3,890x380x76x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "DrawRectangle,1,390x640x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,391x641x74x126\n" +
            "DrawAction,NORMAL,390x640x76x128,640x640x76x128,NORMAL\n" +
            "DrawArrow,2,RIGHT,631x701x5x6,b2a7a7a7\n" +
            "DrawTruncatedText,3,fragment4,390x630x76x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "DrawRectangle,1,640x890x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,641x891x74x126\n" +
            "DrawAction,NORMAL,640x890x76x128,640x640x76x128,NORMAL\n" +
            "DrawArrow,2,UP,675x772x6x5,b2a7a7a7\n" +
            "DrawTruncatedText,3,fragment5,640x880x76x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "DrawRectangle,1,890x890x76x128,ffa7a7a7,1,0\n" +
            "DrawPreviewUnavailable,891x891x74x126\n" +
            "DrawAction,NORMAL,890x890x76x128,640x640x76x128,NORMAL\n" +
            "DrawArrow,2,UP,675x772x6x5,b2a7a7a7\n" +
            "DrawTruncatedText,3,fragment6,890x880x76x5,ff656565,Default:0:9,false\n" +
            "\n" +
            "UNClip\n", list.serialize()
    )
  }
}

