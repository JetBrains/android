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
package com.android.tools.idea.common.scene.target

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.SceneTest

class CommonDragTargetTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_DRAG_PLACEHOLDER.override(true)
  }

  override fun tearDown() {
    StudioFlags.NELE_DRAG_PLACEHOLDER.clearOverride()
    super.tearDown()
  }

  fun testDragMultipleComponentsInConstraintLayout() {
    // Test drag multiple components inside constraint layout
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!

    myInteraction.select(textView, textView2)
    myInteraction.mouseDown(50f, 50f)
    myInteraction.mouseRelease(150f, 200f)

    textView.authoritativeNlComponent.let {
      assertEquals("100dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("150dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }

    textView2.authoritativeNlComponent.let {
      assertEquals("200dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("150dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }
  }

  fun testDragMultipleComponentsToLinearLayout() {
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!

    val linearLayout = myScreen.get("@id/linear").sceneComponent!!
    val button = myScreen.get("@id/button").sceneComponent!!

    myInteraction.select(textView, textView2)
    myInteraction.mouseDown("textView")
    myInteraction.mouseRelease((button.drawX + button.drawWidth / 2).toFloat(), button.drawY.toFloat())

    textView.authoritativeNlComponent.let {
      assertNull(it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertNull(it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }

    textView2.authoritativeNlComponent.let {
      assertNull(it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertNull(it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }

    assertTrue(linearLayout.children.contains(textView))
    assertTrue(linearLayout.children.contains(textView2))
  }

  fun testDragComponentButCancel() {
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val linearLayout = myScreen.get("@id/linear").sceneComponent!!
    val button = myScreen.get("@id/button").sceneComponent!!

    myInteraction.select(textView)
    myInteraction.mouseDown("textView")
    myInteraction.mouseCancel((button.drawX + button.drawWidth / 2).toFloat(), button.drawY.toFloat())

    textView.authoritativeNlComponent.let {
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }
    assertFalse(linearLayout.children.contains(textView))
  }

  fun testDragMultipleComponentsButCancel() {
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!

    val linearLayout = myScreen.get("@id/linear").sceneComponent!!
    val button = myScreen.get("@id/button").sceneComponent!!

    myInteraction.select(textView, textView2)
    myInteraction.mouseDown("textView")
    myInteraction.mouseCancel((button.drawX + button.drawWidth / 2).toFloat(), button.drawY.toFloat())

    textView.authoritativeNlComponent.let {
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }

    textView2.authoritativeNlComponent.let {
      assertEquals("100dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }

    assertFalse(linearLayout.children.contains(textView))
    assertFalse(linearLayout.children.contains(textView2))
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(SdkConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 2000, 2000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(0, 0, 200, 200)
                       .id("@id/textView")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "0dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "0dp"),
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(200, 0, 200, 200)
                       .id("@id/textView2")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "100dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "0dp"),
                     component(SdkConstants.LINEAR_LAYOUT)
                       .withBounds(1000, 1000, 1000, 1000)
                       .id("@id/linear")
                       .width("500dp")
                       .height("500dp")
                       .withAttribute(SdkConstants.ATTR_ORIENTATION, SdkConstants.VALUE_VERTICAL)
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "500dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "500dp")
                       .children(
                         component(SdkConstants.BUTTON)
                           .withBounds(1000, 1000, 200, 200)
                           .id("@id/button")
                           .width("100dp")
                           .height("100dp"),
                         component(SdkConstants.BUTTON)
                           .withBounds(1000, 1200, 200, 200)
                           .id("@id/button2")
                           .width("100dp")
                           .height("100dp")
                       )
                   )
    )
  }
}
