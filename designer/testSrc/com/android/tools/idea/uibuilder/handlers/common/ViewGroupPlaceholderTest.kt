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
package com.android.tools.idea.uibuilder.handlers.common

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class ViewGroupPlaceholderTest : SceneTest() {

  fun testRegion() {
    val viewGroup = myScene.getSceneComponent("viewGroup")!!

    val placeholder = ViewGroupPlaceholder(viewGroup)

    val region = placeholder.region
    assertEquals(viewGroup.drawX, region.left)
    assertEquals(viewGroup.drawY, region.top)
    assertEquals(viewGroup.drawX + viewGroup.drawWidth, region.right)
    assertEquals(viewGroup.drawY + viewGroup.drawHeight, region.bottom)
  }

  fun testSnapSucceed() {
    val viewGroup = myScene.getSceneComponent("viewGroup")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = ViewGroupPlaceholder(viewGroup)

    val left = viewGroup.drawX
    val top = viewGroup.drawY
    val right = left + textView.drawWidth
    val bottom = top + textView.drawHeight

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)

    assertTrue(snappedResult)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  fun testSnapFailed() {
    val viewGroup = myScene.getSceneComponent("viewGroup")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = ViewGroupPlaceholder(viewGroup)

    val left = viewGroup.drawX - textView.drawWidth
    val top = viewGroup.drawY - textView.drawHeight
    val right = left + textView.drawWidth
    val bottom = top + textView.drawHeight

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)

    assertFalse(snappedResult)
    assertEquals(-1, p.x)
    assertEquals(-1, p.y)
  }

  fun testApply() {
    val viewGroup = myScene.getSceneComponent("viewGroup")!!
    val textView = myScene.getSceneComponent("textView")!!

    assertEquals(200, textView.drawX)
    assertEquals(0, textView.drawY)

    val placeholder = ViewGroupPlaceholder(viewGroup)

    applyPlaceholderToSceneComponent(textView, placeholder)

    mySceneManager.update()
    assertTrue(viewGroup.children.contains(textView))

    assertEquals(0, textView.drawX)
    assertTrue(viewGroup.children.contains(textView))
  }

  override fun createModel(): ModelBuilder {
    return model(
      "view_group.xml",
      component(SdkConstants.LINEAR_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(
          component(SdkConstants.VIEW_GROUP)
            .withBounds(0, 0, 400, 400)
            .id("@id/viewGroup")
            .width("200dp")
            .height("200dp")
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 200, 200)
                .width("100dp")
                .height("100dp")
            ),
          component(SdkConstants.TEXT_VIEW)
            .withBounds(400, 0, 200, 200)
            .id("@id/textView")
            .width("100dp")
            .height("100dp"),
        ),
    )
  }
}
