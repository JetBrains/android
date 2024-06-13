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
package com.android.tools.idea.uibuilder.handlers.absolute

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class AbsolutePlaceholderTest : SceneTest() {

  fun testRegion() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val placeholder = AbsolutePlaceholder(absoluteLayout)

    val region = placeholder.region
    assertEquals(absoluteLayout.drawX, region.left)
    assertEquals(absoluteLayout.drawY, region.top)
    assertEquals(absoluteLayout.drawX + absoluteLayout.drawWidth, region.right)
    assertEquals(absoluteLayout.drawY + absoluteLayout.drawHeight, region.bottom)
  }

  fun testSnapFailed() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = AbsolutePlaceholder(absoluteLayout)

    val left = absoluteLayout.drawX - textView.drawWidth - 10
    val top = absoluteLayout.drawY - textView.drawHeight - 10
    val right = absoluteLayout.drawX - 10
    val bottom = absoluteLayout.drawY - 10

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    assertFalse(snappedResult)
    assertEquals(-1, p.x)
    assertEquals(-1, p.y)
  }

  fun testSnapSucceedIfCenterIsInside() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val placeholder = AbsolutePlaceholder(absoluteLayout)

    // The center point is at (absoluteLayout.drawX + 10, absoluteLayout.drawY +10), which is inside
    // absoluteLayout.
    val left = absoluteLayout.drawX - 10
    val top = absoluteLayout.drawY - 10
    val right = absoluteLayout.drawX + 30
    val bottom = absoluteLayout.drawY + 30

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    assertTrue(snappedResult)
    assertEquals(absoluteLayout.drawX - 10, p.x)
    assertEquals(absoluteLayout.drawY - 10, p.y)
  }

  fun testSnapFailedIfCenterIsOutside() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val placeholder = AbsolutePlaceholder(absoluteLayout)

    // The center point is at (absoluteLayout.drawX - 20, absoluteLayout.drawY - 20), which is
    // outside absoluteLayout.
    val left = absoluteLayout.drawX - 50
    val top = absoluteLayout.drawY - 50
    val right = absoluteLayout.drawX + 10
    val bottom = absoluteLayout.drawY + 10

    assertFalse(placeholder.snap(SnappingInfo(left, top, right, bottom), Point(-1, -1)))
  }

  fun testSnapSucceed() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = AbsolutePlaceholder(absoluteLayout)

    val left = absoluteLayout.drawX
    val top = absoluteLayout.drawY
    val right = absoluteLayout.drawX + textView.drawWidth
    val bottom = absoluteLayout.drawY + textView.drawHeight

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    assertTrue(snappedResult)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  fun testApply() {
    val absoluteLayout = myScene.getSceneComponent("absolute")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = AbsolutePlaceholder(absoluteLayout)
    textView.setPosition(absoluteLayout.drawX + 50, absoluteLayout.drawX + 60)
    mySceneManager.update()

    applyPlaceholderToSceneComponent(textView, placeholder)

    mySceneManager.update()

    val nlComponent = textView.authoritativeNlComponent
    assertEquals(
      "50dp",
      nlComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_X),
    )
    assertEquals(
      "60dp",
      nlComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_Y),
    )
  }

  override fun createModel(): ModelBuilder {
    return model(
      "absolute.xml",
      component(SdkConstants.ABSOLUTE_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .id("@id/absolute")
        .matchParentWidth()
        .matchParentHeight()
        .children(
          component(SdkConstants.TEXT_VIEW)
            .withBounds(0, 0, 200, 200)
            .id("@id/textView")
            .width("100dp")
            .height("100dp")
        ),
    )
  }
}
