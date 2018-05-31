/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene

import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget

class NavSceneManagerTest : NavTestCase() {

  fun testLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }
    val scene = model.surface.scene!!


    val fragment2x = scene.getSceneComponent("fragment2")!!.drawX
    val fragment2y = scene.getSceneComponent("fragment2")!!.drawY
    val fragment3x = scene.getSceneComponent("fragment3")!!.drawX
    val fragment3y = scene.getSceneComponent("fragment3")!!.drawY

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val dragTarget = sceneComponent.targets.filterIsInstance(ScreenDragTarget::class.java).first()
    dragTarget.mouseDown(0, 0)
    sceneComponent.isDragging = true
    sceneComponent.setPosition(100, 50)
    // the release position isn't used
    dragTarget.mouseRelease(2, 2, listOf())

    scene.sceneManager.requestRender()

    assertEquals(100, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(50, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(fragment2x, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(fragment2y, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(fragment3x, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(fragment3y, scene.getSceneComponent("fragment3")!!.drawY)
  }

  fun testLandscape() {
    val model = NavModelBuilderUtil.model("nav.xml", myFacet, myFixture, {
      navigation {
        fragment("fragment1")
      }
    }, "navigation-land").build()
    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    assertEquals(256, component.drawWidth)
    assertEquals(153, component.drawHeight)
  }

  fun testPortrait() {
    val model = NavModelBuilderUtil.model("nav.xml", myFacet, myFixture, {
      navigation {
        fragment("fragment1")
      }
    }, "navigation-port").build()
    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    assertEquals(153, component.drawWidth)
    assertEquals(256, component.drawHeight)
  }
}