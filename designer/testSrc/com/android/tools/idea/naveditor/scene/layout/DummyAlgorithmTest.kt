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
package com.android.tools.idea.naveditor.scene.layout

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Tests for [DummyAlgorithm]
 */
class DummyAlgorithmTest : NavTestCase() {

  /**
   * Just lay out some components using this algorithm. The basic layout will be:
   *
   * |---------|
   * | 1  2  3 |
   * | 4       |
   * |---------|
   */
  fun testSimple() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
        fragment("fragment4")
      }
    }
    model.surface.sceneManager!!.update()
    val scene = model.surface.scene!!
    val root = scene.root!!
    val algorithm = DummyAlgorithm(NavigationSchema.get(myFacet))
    root.flatten().forEach { it.setPosition(-500, -500) }
    root.flatten().forEach { algorithm.layout(it) }

    assertEquals(20, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(20, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(200, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(20, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(380, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(20, scene.getSceneComponent("fragment3")!!.drawY)
    assertEquals(20, scene.getSceneComponent("fragment4")!!.drawX)
    assertEquals(320, scene.getSceneComponent("fragment4")!!.drawY)
  }

  /**
   * Test that we don't overlap manually-placed components. The layout will be:
   *
   * |------------|
   * | 22      33 |
   * | 22      33 |
   * | 22  mm  33 |
   * |     mm     |
   * | 44  mm  55 |
   * | 44      55 |
   * | 44  66  55 |
   * |     66     |
   * | 77  66     |
   * | 77         |
   * | 77         |
   * |------------|
   */
  fun testSkipOther() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
        fragment("fragment4")
        fragment("fragment5")
        fragment("fragment6")
        fragment("fragment7")
      }
    }
    model.surface.sceneManager!!.update()
    val scene = model.surface.scene!!
    val root = scene.root!!
    val algorithm = DummyAlgorithm(NavigationSchema.get(myFacet))
    val manual = scene.getSceneComponent("fragment1")!!
    root.flatten().forEach { c -> c.setPosition(-500, -500) }
    manual.setPosition(190, 100)
    root.flatten().filter { it !== manual }.forEach { algorithm.layout(it) }

    assertEquals(190, manual.drawX)
    assertEquals(100, manual.drawY)

    assertEquals(20, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(20, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(380, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(20, scene.getSceneComponent("fragment3")!!.drawY)
    assertEquals(20, scene.getSceneComponent("fragment4")!!.drawX)
    assertEquals(320, scene.getSceneComponent("fragment4")!!.drawY)
    assertEquals(380, scene.getSceneComponent("fragment5")!!.drawX)
    assertEquals(320, scene.getSceneComponent("fragment5")!!.drawY)
    assertEquals(200, scene.getSceneComponent("fragment6")!!.drawX)
    assertEquals(380, scene.getSceneComponent("fragment6")!!.drawY)
    assertEquals(20, scene.getSceneComponent("fragment7")!!.drawX)
    assertEquals(620, scene.getSceneComponent("fragment7")!!.drawY)
  }
}
