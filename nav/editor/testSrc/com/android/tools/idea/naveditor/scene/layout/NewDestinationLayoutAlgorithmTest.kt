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
package com.android.tools.idea.naveditor.scene.layout

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase

class NewDestinationLayoutAlgorithmTest : NavTestCase() {
  fun testLayout() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
        fragment("fragment4")
      }
    }

    model.treeReader.find("fragment1")!!.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)
    model.treeReader.find("fragment2")!!.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)
    model.treeReader.find("fragment3")!!.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)

    model.surface.sceneManager!!.requestRenderAsync()
    val scene = model.surface.scene!!
    assertEquals(-708, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(-708, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(-648, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(-648, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(-588, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(-588, scene.getSceneComponent("fragment3")!!.drawY)
  }
}