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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.model.layoutHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.awt.Point

class CoordinatorPlaceholderTest : SceneTest() {

  fun testRegion() {
    val coordinatorLayout = myScene.getSceneComponent("coordinator")!!
    val frameLayout = myScene.getSceneComponent("frame")!!

    run {
      val placeholder = CoordinatorPlaceholder(coordinatorLayout, frameLayout, CoordinatorPlaceholder.Type.LEFT_TOP)
      val region = placeholder.region
      assertEquals(frameLayout.drawX, region.left)
      assertEquals(frameLayout.drawY, region.top)
      assertEquals(frameLayout.drawX + SIZE, region.right)
      assertEquals(frameLayout.drawY + SIZE, region.bottom)
    }

    run {
      val placeholder = CoordinatorPlaceholder(coordinatorLayout, frameLayout, CoordinatorPlaceholder.Type.RIGHT_BOTTOM)
      val region = placeholder.region
      assertEquals(frameLayout.drawX + frameLayout.drawWidth - SIZE, region.left)
      assertEquals(frameLayout.drawY + frameLayout.drawHeight - SIZE, region.top)
      assertEquals(frameLayout.drawX + frameLayout.drawWidth, region.right)
      assertEquals(frameLayout.drawY + frameLayout.drawHeight, region.bottom)
    }
  }

  fun testSnap() {
    val coordinatorLayout = myScene.getSceneComponent("coordinator")!!
    val frameLayout = myScene.getSceneComponent("frame")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = CoordinatorPlaceholder(coordinatorLayout, frameLayout, CoordinatorPlaceholder.Type.LEFT_TOP)
    val left = frameLayout.drawX + SIZE / 2 - textView.drawWidth / 2 + 3
    val top = frameLayout.drawY + SIZE / 2 - textView.drawHeight / 2 + 4
    val right = left + textView.drawWidth
    val bottom = top + textView.drawHeight

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(SnappingInfo(left, top, right, bottom), p)
    assertTrue(snappedResult)
    assertEquals(frameLayout.drawX + SIZE / 2 - textView.drawWidth / 2, p.x)
    assertEquals(frameLayout.drawY + SIZE / 2 - textView.drawHeight / 2, p.y)
    assertEquals(5.toDouble(), p.distance(left.toDouble(), top.toDouble()), 0.01)
  }

  fun testApplyToLeftTop() {
    val coordinatorLayout = myScene.getSceneComponent("coordinator")!!
    val frameLayout = myScene.getSceneComponent("frame")!!
    val textView = myScene.getSceneComponent("textView")!!

    assertEquals(200, textView.drawX)
    assertEquals(0, textView.drawY)

    val placeholder = CoordinatorPlaceholder(coordinatorLayout, frameLayout, CoordinatorPlaceholder.Type.LEFT_TOP)
    applyPlaceholderToSceneComponent(textView, placeholder)

    mySceneManager.update()
    assertTrue(coordinatorLayout.children.contains(textView))

    assertEquals(frameLayout.drawX, textView.drawX)
    assertEquals(frameLayout.drawY, textView.drawY)
    assertEquals("@+id/frame", textView.nlComponent.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR))
    assertEquals("start|top", textView.nlComponent.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY))
  }

  fun testApplyToRightBottom() {
    val coordinatorLayout = myScene.getSceneComponent("coordinator")!!
    val frameLayout = myScene.getSceneComponent("frame")!!
    val textView = myScene.getSceneComponent("textView")!!

    assertEquals(200, textView.drawX)
    assertEquals(0, textView.drawY)

    val placeholder = CoordinatorPlaceholder(coordinatorLayout, frameLayout, CoordinatorPlaceholder.Type.RIGHT_BOTTOM)
    applyPlaceholderToSceneComponent(textView, placeholder)

    mySceneManager.update()
    assertTrue(coordinatorLayout.children.contains(textView))

    assertEquals(frameLayout.drawX + frameLayout.drawWidth - textView.drawWidth, textView.drawX)
    assertEquals(frameLayout.drawY + + frameLayout.drawHeight - textView.drawHeight, textView.drawY)
    assertEquals("@+id/frame", textView.nlComponent.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR))
    assertEquals("end|bottom", textView.nlComponent.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY))
  }

  fun testAddComponentWithoutSnappingToAnchor() {
    val coordinatorLayout = myScene.getSceneComponent("coordinator")!!
    val placeholders = coordinatorLayout.nlComponent.layoutHandler!!.getPlaceholders(coordinatorLayout, emptyList())

    val left = 100
    val top = 120

    val p = Point()
    val snappedPlaceholders = placeholders.filter { it.snap(SnappingInfo(left, top, left + 50, top + 50), p) }.toList()

    assertSize(1, snappedPlaceholders)
    UsefulTestCase.assertInstanceOf(snappedPlaceholders[0], ViewGroupPlaceholder::class.java)
    TestCase.assertEquals(left, p.x)
    TestCase.assertEquals(top, p.y)
  }

  override fun createModel(): ModelBuilder {
    return model("coordinator.xml",
                 component(SdkConstants.LINEAR_LAYOUT)
                   .withBounds(0, 0, 1000, 1000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(AndroidXConstants.COORDINATOR_LAYOUT.newName())
                       .withBounds(0, 0, 400, 400)
                       .id("@id/coordinator")
                       .width("200dp")
                       .height("200dp")
                       .children(
                         component(SdkConstants.FRAME_LAYOUT)
                           .withBounds(0, 0, 400, 400)
                           .id("@id/frame")
                           .width("200dp")
                           .height("200dp")
                       ),
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(400, 0, 60, 60)
                       .id("@id/textView")
                       .width("30dp")
                       .height("30dp")
                   )
    )
  }
}
