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
package com.android.tools.idea.uibuilder.handlers.linear

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.model.layoutHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class LinearPlaceholderTest : SceneTest() {

  fun testRegion() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           textView1,
                                                           linearLayout.drawX,
                                                           linearLayout.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    val region = placeholder.region
    assertEquals(linearLayout.drawX - SIZE, region.left)
    assertEquals(linearLayout.drawY, region.top)
    assertEquals(linearLayout.drawX + SIZE, region.right)
    assertEquals(linearLayout.drawY + linearLayout.drawHeight, region.bottom)
  }

  fun testSnap() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           textView1,
                                                           linearLayout.drawX,
                                                           linearLayout.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    val left = linearLayout.drawX - 5 - textView1.drawWidth / 2
    val top = linearLayout.drawY + 5 - textView1.drawHeight / 2
    val right = left + textView1.drawWidth
    val bottom = top + textView1.drawHeight

    val p = Point()
    placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    val distance = p.distance(left.toDouble(), top.toDouble())
    assertEquals(left + 5, p.x)
    assertEquals(top, p.y)
    assertEquals(5.0, distance, 0.01)
  }

  fun testMovingComponentToHead() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!
    val button = myScene.getSceneComponent("button")!!
    val textView2 = myScene.getSceneComponent("myText2")!!

    assertEquals(200, textView2.drawX)

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           textView1,
                                                           linearLayout.drawX,
                                                           linearLayout.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    textView2.setPosition(linearLayout.drawX, linearLayout.drawY)
    applyPlaceholderToSceneComponent(textView2, placeholder)

    // The SceneComponent is used even the NlModel is changed. We check the position of applied component here.
    assertEquals(0, textView2.drawX)
    assertEquals(100, textView1.drawX)
    assertEquals(200, button.drawX)
  }

  fun testMovingComponentUp() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!
    val button = myScene.getSceneComponent("button")!!
    val textView2 = myScene.getSceneComponent("myText2")!!

    assertEquals(200, textView2.drawX)

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           button,
                                                           button.drawX,
                                                           button.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    textView2.setPosition(button.drawX, button.drawY)
    applyPlaceholderToSceneComponent(textView2, placeholder)

    // The SceneComponent is used even the NlModel is changed. We check the position of applied component here.
    assertEquals(0, textView1.drawX)
    assertEquals(100, textView2.drawX)
    assertEquals(200, button.drawX)
  }

  fun testMovingComponentDown() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!
    val button = myScene.getSceneComponent("button")!!
    val textView2 = myScene.getSceneComponent("myText2")!!

    assertEquals(0, textView1.drawX)

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           textView2,
                                                           button.drawX,
                                                           button.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    textView1.setPosition(textView2.drawX, textView2.drawY)
    applyPlaceholderToSceneComponent(textView1, placeholder)

    // The SceneComponent is used even the NlModel is changed. We check the position of applied component here.
    assertEquals(0, button.drawX)
    assertEquals(100, textView1.drawX)
    assertEquals(200, textView2.drawX)
  }

  fun testMovingComponentToTail() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val textView1 = myScene.getSceneComponent("myText1")!!
    val button = myScene.getSceneComponent("button")!!
    val textView2 = myScene.getSceneComponent("myText2")!!

    assertEquals(0, textView1.drawX)

    val placeholder =
      LinearPlaceholderFactory.createHorizontalPlaceholder(linearLayout,
                                                           null,
                                                           textView2.drawX + textView2.drawWidth,
                                                           textView2.drawY,
                                                           linearLayout.drawY + linearLayout.drawHeight)

    textView1.setPosition(textView2.drawX + textView2.drawWidth, textView2.drawY)
    applyPlaceholderToSceneComponent(textView1, placeholder)

    assertEquals(0, button.drawX)
    assertEquals(100, textView2.drawX)
    assertEquals(200, textView1.drawX)
  }

  fun testAddComponentWithoutSnappingToSeparator() {
    val linearLayout = myScene.getSceneComponent("linear")!!
    val placeholders = linearLayout.nlComponent.layoutHandler!!.getPlaceholders(linearLayout, emptyList())

    val left = 50
    val top = 50

    val p = Point()
    val snappedPlaceholders = placeholders.filter { it.snap(SnappingInfo(left, top, left + 50, top + 50), p) }.toList()

    assertSize(1, snappedPlaceholders)
    assertInstanceOf(snappedPlaceholders[0], ViewGroupPlaceholder::class.java)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  override fun createModel(): ModelBuilder {
    return model("linear.xml",
             component(SdkConstants.LINEAR_LAYOUT)
               .withBounds(0, 0, 1000, 1000)
               .id("@id/linear")
               .matchParentWidth()
               .matchParentHeight()
               .children(
                 component(SdkConstants.TEXT_VIEW)
                   .withBounds(0, 0, 200, 200)
                   .id("@id/myText1")
                   .width("100dp")
                   .height("100dp"),
                 component(SdkConstants.BUTTON)
                   .withBounds(200, 0, 200, 200)
                   .id("@id/button")
                   .width("100dp")
                   .height("100dp"),
                 component(SdkConstants.TEXT_VIEW)
                   .withBounds(400, 0, 200, 200)
                   .id("@id/myText2")
                   .width("100dp")
                   .height("100dp")
               ))
  }
}
