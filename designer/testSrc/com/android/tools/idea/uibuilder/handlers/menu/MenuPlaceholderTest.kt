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
package com.android.tools.idea.uibuilder.handlers.menu

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.menu.MenuPlaceholder
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class MenuPlaceholderTest : SceneTest() {

  fun testRootMenuRegion() {
    val root = myScene.getSceneComponent("root_menu")!!
    val placeholder = MenuPlaceholder(root)
    val sceneView = mySceneManager.sceneView

    val region = placeholder.region

    assertEquals(region.left, Coordinates.getAndroidXDip(sceneView, sceneView.x))
    assertEquals(region.top, Coordinates.getAndroidYDip(sceneView, sceneView.y))
    assertEquals(region.right - placeholder.region.left, Coordinates.getAndroidDimensionDip(sceneView, sceneView.size.width))
    assertEquals(region.bottom - placeholder.region.top, Coordinates.getAndroidDimensionDip(sceneView, sceneView.size.height))
  }

  fun testNestedMenuRegion() {
    val item2 = myScene.getSceneComponent("item2")!!
    val nestedMenu = myScene.getSceneComponent("nested_menu")!!

    val placeholder = MenuPlaceholder(nestedMenu)
    val region = placeholder.region

    assertEquals(item2.drawX + item2.drawWidth - item2.drawWidth / 4, region.left)
    assertEquals(item2.drawY, region.top)
    assertEquals(item2.drawX + item2.drawWidth, region.right)
    assertEquals(item2.drawY + item2.drawHeight, region.bottom)
  }

  fun testSnapRootMenu() {
    run {
      // Try to snap a <item> to root menu. This should always work
      val menu = myScene.getSceneComponent("root_menu")!!
      val placeholder = MenuPlaceholder(menu)

      val left = placeholder.region.left - 5
      val top = placeholder.region.top + 5
      val right = left + 100
      val bottom = top + 50

      assertTrue(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_ITEM), Point()))
    }

    run {
      // Try to snap a non-item component to root menu. This should not work
      val menu = myScene.getSceneComponent("root_menu")!!
      val placeholder = MenuPlaceholder(menu)

      val left = placeholder.region.left - 5
      val top = placeholder.region.top + 5
      val right = left + 100
      val bottom = top + 50

      assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_MENU), Point()))
    }
  }
  fun testSnapNestedMenu() {
    run {
      // Try to snap a <item> to nested menu. This should always work
      val menu = myScene.getSceneComponent("nested_menu")!!
      val placeholder = MenuPlaceholder(menu)

      val left = placeholder.region.left - 1
      val top = placeholder.region.top + 1
      val right = placeholder.region.right - 1
      val bottom = placeholder.region.bottom + 1

      assertTrue(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_ITEM), Point()))
    }

    run {
      // Try to snap a non-item component (e.g. menu) to nested menu. This should not work
      val menu = myScene.getSceneComponent("nested_menu")!!
      val placeholder = MenuPlaceholder(menu)

      val left = placeholder.region.left - 5
      val top = placeholder.region.top + 5
      val right = left + 100
      val bottom = top + 50

      assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_MENU), Point()))
    }
  }

  override fun createModel(): ModelBuilder {
    return model("menu.xml",
                 component(SdkConstants.TAG_MENU)
                   .id("@id/root_menu")
                   .withBounds(0, 0, 1000, 1000)
                   .children(
                     component(SdkConstants.TAG_ITEM)
                       .withBounds(0, 0, 200, 200)
                       .id("@id/item1")
                       .width("100dp")
                       .height("100dp"),
                     component(SdkConstants.TAG_ITEM)
                       .withBounds(0, 200, 200, 200)
                       .id("@id/item2")
                       .width("100dp")
                       .height("100dp")
                       .children(
                         component(SdkConstants.TAG_MENU)
                           .withBounds(0, 200, 0, 0)
                           .id("@id/nested_menu")
                           .children(
                             component(SdkConstants.TAG_ITEM)
                               .id("@id/nested_item")
                               .withBounds(0, 200, 0, 0)
                           )
                       ),
                     component(SdkConstants.TAG_ITEM)
                       .withBounds(0, 400, 200, 200)
                       .id("@id/item3")
                       .width("100dp")
                       .height("100dp")
                   ))
  }
}
