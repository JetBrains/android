/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.model.viewHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class LayoutHandlerTest : SceneTest() {

  fun testRegion() {
    val layoutComponent = myScene.root!!
    val ph = layoutComponent.nlComponent.viewHandler!!.getPlaceholders(layoutComponent)[0]

    val sceneView = myScreen.screen
    val size = sceneView.size
    val width = Coordinates.getAndroidDimensionDip(sceneView, size.width)
    val height = Coordinates.getAndroidDimensionDip(sceneView, size.height)
    assertEquals(Region(0, 0, width, height), ph.region)
  }

  fun testSnapSucceed() {
    val layoutComponent = myScene.root!!
    val ph = layoutComponent.nlComponent.viewHandler!!.getPlaceholders(layoutComponent)[0]
    val p = Point()
    mySceneManager.update()
    assertTrue(ph.snap(SnappingInfo(50, 60, 150, 160), p))
    assertEquals(50, p.x)
    assertEquals(60, p.y)
  }

  fun testSnapFailed() {
    val layoutComponent = myScene.root!!
    val ph = layoutComponent.nlComponent.viewHandler!!.getPlaceholders(layoutComponent)[0]
    val p = Point()
    assertFalse(ph.snap(SnappingInfo(600, 600, 700, 700), p))
  }

  override fun createModel(): ModelBuilder = model("layout.xml",
                                                   component(SdkConstants.TAG_LAYOUT)
                                                     .withBounds(0, 0, 1000, 1000))
}
