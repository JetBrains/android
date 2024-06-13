/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.AndroidXConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class TabLayoutPlaceholderTest : SceneTest() {

  fun testRegion() {
    val tabLayout = myScene.getSceneComponent("tabLayout")!!
    val tabItem1 = myScene.getSceneComponent("tabItem1")!!

    val placeholder = TabLayoutPlaceholder(tabLayout, tabItem1)

    val region = placeholder.region
    assertEquals(
      tabLayout.drawX - com.android.tools.idea.uibuilder.handlers.linear.SIZE,
      region.left,
    )
    assertEquals(tabLayout.drawY, region.top)
    assertEquals(
      tabLayout.drawX + com.android.tools.idea.uibuilder.handlers.linear.SIZE,
      region.right,
    )
    assertEquals(tabLayout.drawY + tabLayout.drawHeight, region.bottom)
  }

  fun testSnap() {
    val tabLayout = myScene.getSceneComponent("tabLayout")!!
    val tabItem1 = myScene.getSceneComponent("tabItem1")!!

    val placeholder = TabLayoutPlaceholder(tabLayout, tabItem1)

    val left = tabLayout.drawX - 5 - tabItem1.drawWidth / 2
    val top = tabLayout.drawY + 5 - tabItem1.drawHeight / 2
    val right = left + tabItem1.drawWidth
    val bottom = top + tabItem1.drawHeight

    val p = Point()
    placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    val distance = p.distance(left.toDouble(), top.toDouble())
    assertEquals(left + 5, p.x)
    assertEquals(top, p.y)
    assertEquals(5.0, distance, 0.01)
  }

  override fun createModel(): ModelBuilder {
    return model(
      "tabLayout.xml",
      component(AndroidXConstants.TAB_LAYOUT.newName())
        .width("300dp")
        .height("100dp")
        .withBounds(0, 0, 600, 200)
        .id("@id/tabLayout")
        .children(
          component(AndroidXConstants.TAB_ITEM.newName())
            .withBounds(0, 0, 200, 200)
            .id("@id/tabItem1")
            .width("100dp")
            .height("100dp"),
          component(AndroidXConstants.TAB_ITEM.newName())
            .withBounds(200, 0, 200, 200)
            .id("@id/tabItem2")
            .width("100dp")
            .height("100dp"),
          component(AndroidXConstants.TAB_ITEM.newName())
            .withBounds(400, 0, 200, 200)
            .id("@id/tabItem3")
            .width("100dp")
            .height("100dp"),
        ),
    )
  }
}
