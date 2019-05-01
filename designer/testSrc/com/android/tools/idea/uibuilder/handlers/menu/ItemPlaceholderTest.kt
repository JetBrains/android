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
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.menu.ItemPlaceholder
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class ItemPlaceholderTest : SceneTest() {

  fun testRegion() {
    val item = myScene.getSceneComponent("item1")!!
    val placeholder = ItemPlaceholder(item)
    val region = placeholder.region

    assertEquals(item.drawX, region.left)
    assertEquals(item.drawY, region.top)
    assertEquals(item.drawX + item.drawWidth, region.right)
    assertEquals(item.drawY + item.drawHeight, region.bottom)
  }

  fun testSnap() {
    run {
      // Try to snap a <menu> to item 1. This should work
      val item = myScene.getSceneComponent("item1")!!
      val placeholder = ItemPlaceholder(item)

      val left = item.drawX - 5
      val top = item.drawY + 5
      val right = left + item.drawWidth
      val bottom = top + item.drawHeight

      val p = Point()
      assertTrue(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_MENU), p))
    }

    run {
      // Try to snap a non-menu component to item 1. This should not work
      val item = myScene.getSceneComponent("item1")!!
      val placeholder = ItemPlaceholder(item)

      val left = item.drawX - 5 - item.drawWidth / 2
      val top = item.drawY + 5 - item.drawHeight / 2
      val right = left + item.drawWidth
      val bottom = top + item.drawHeight

      val p = Point()
      assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_ITEM), p))
    }

    run {
      // Try to snap a <menu> to item 2. Item 2 has <menu> already so it should be failed.
      val item = myScene.getSceneComponent("item2")!!
      val placeholder = ItemPlaceholder(item)

      val left = item.drawX - 5 - item.drawWidth / 2
      val top = item.drawY + 5 - item.drawHeight / 2
      val right = left + item.drawWidth
      val bottom = top + item.drawHeight

      val p = Point()
      assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_MENU), p))
    }

    run {
      // Try to snap a <menu> to nested item. This should be failed.
      val item = myScene.getSceneComponent("nested_item")!!
      val placeholder = ItemPlaceholder(item)

      val left = item.drawX - 5 - item.drawWidth / 2
      val top = item.drawY + 5 - item.drawHeight / 2
      val right = left + item.drawWidth
      val bottom = top + item.drawHeight

      val p = Point()
      assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom, SdkConstants.TAG_MENU), p))
    }
  }

  override fun createModel(): ModelBuilder {
    return model("menu.xml",
                 component(SdkConstants.TAG_MENU)
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
