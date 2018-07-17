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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class ConstraintPlaceholderTest : SceneTest() {

  fun testRegion() {
    val constraint = myScene.getSceneComponent("constraint")!!
    val placeholder = ConstraintPlaceholder(constraint)

    val region = placeholder.region
    assertEquals(constraint.drawX, region.left)
    assertEquals(constraint.drawY, region.top)
    assertEquals(constraint.drawX + constraint.drawWidth, region.right)
    assertEquals(constraint.drawY + constraint.drawHeight, region.bottom)
  }

  fun testSnapFailed() {
    val constraint = myScene.getSceneComponent("constraint")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val left = constraint.drawX - 30
    val top = constraint.drawY - 30

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(left, top, left + 10, top + 10, p)
    assertFalse(snappedResult)
    assertEquals(-1, p.x)
    assertEquals(-1, p.y)
  }

  fun testSnapSucceed() {
    val constraint = myScene.getSceneComponent("constraint")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val left = constraint.drawX + 10
    val top = constraint.drawY + 10

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(left, top, left + 10, top + 10, p)
    assertTrue(snappedResult)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  fun testApply() {
    val constraint = myScene.getSceneComponent("constraint")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val mouseX = constraint.drawX + 50
    val mouseY = constraint.drawX + 60
    textView.setPosition(mouseX, mouseY)

    mySceneManager.update()

    val appliedResult = applyPlaceholderToSceneComponent(textView, placeholder)
    assertTrue(appliedResult)

    assertEquals("50dp", textView.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
    assertEquals("60dp", textView.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(SdkConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 1000, 1000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(0, 0, 200, 200)
                       .id("@id/textView")
                       .width("100dp")
                       .height("100dp")
                   )
    )
  }
}
