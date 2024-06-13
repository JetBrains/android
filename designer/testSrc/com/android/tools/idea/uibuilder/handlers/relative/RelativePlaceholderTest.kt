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
package com.android.tools.idea.uibuilder.handlers.relative

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class RelativePlaceholderTest : SceneTest() {

  fun testRegion() {
    val relativeLayout = myScene.getSceneComponent("relative")!!
    val placeholder = RelativePlaceholder(relativeLayout)

    val region = placeholder.region
    assertEquals(relativeLayout.drawX, region.left)
    assertEquals(relativeLayout.drawY, region.top)
    assertEquals(relativeLayout.drawX + relativeLayout.drawWidth, region.right)
    assertEquals(relativeLayout.drawY + relativeLayout.drawHeight, region.bottom)
  }

  fun testSnapSucceed() {
    val relativeLayout = myScene.getSceneComponent("relative")!!

    val placeholder = RelativePlaceholder(relativeLayout)

    val left = relativeLayout.drawX + relativeLayout.drawWidth / 2
    val top = relativeLayout.drawY + relativeLayout.drawHeight / 2

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, left + 10, top + 10), p)
    assertTrue(snappedResult)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  fun testSnapFailed() {
    val relativeLayout = myScene.getSceneComponent("relative")!!

    val placeholder = RelativePlaceholder(relativeLayout)

    val left = relativeLayout.drawX - 20
    val top = relativeLayout.drawY - 20

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, left + 10, top + 10), p)
    assertFalse(snappedResult)
    assertEquals(-1, p.x)
    assertEquals(-1, p.y)
  }

  fun testApply() {
    val relativeLayout = myScene.getSceneComponent("relative")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = RelativePlaceholder(relativeLayout)
    applyPlaceholderToSceneComponent(textView, placeholder)

    mySceneManager.update()

    assertTrue(relativeLayout.children.contains(textView))
  }

  override fun createModel(): ModelBuilder {
    return model(
      "relative.xml",
      component(SdkConstants.LINEAR_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(
          component(SdkConstants.RELATIVE_LAYOUT)
            .withBounds(0, 0, 500, 500)
            .id("@id/relative")
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 200, 200)
                .width("100dp")
                .height("100dp")
            ),
          component(SdkConstants.TEXT_VIEW)
            .withBounds(500, 0, 200, 200)
            .id("@id/textView")
            .width("100dp")
            .height("100dp"),
        ),
    )
  }
}
