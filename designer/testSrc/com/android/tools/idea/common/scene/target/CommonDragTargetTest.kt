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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.model.layoutHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Cursor
import java.awt.event.InputEvent

class CommonDragTargetTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_DRAG_PLACEHOLDER.override(true)
  }

  override fun tearDown() {
    StudioFlags.NELE_DRAG_PLACEHOLDER.clearOverride()
    super.tearDown()
  }

  fun testDragComponent() {
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val linearLayout = myScreen.get("@id/linear").sceneComponent!!

    myInteraction.select(textView)
    myInteraction.mouseDown("textView")
    myInteraction.mouseRelease(150f, 200f)

    textView.authoritativeNlComponent.let {
      assertEquals("100dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("150dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }
    assertFalse(linearLayout.children.contains(textView))

    assertEquals(1, myScreen.screen.selectionModel.selection.size)
  }

  fun testDragComponentInConstraintLayoutWithSnapping() {
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!

    myInteraction.select(textView2)
    myInteraction.mouseDown("textView2")
    myInteraction.mouseRelease(60f, 60f)

    // The result would be 16dp due to snapped by TargetSnapper. In non-snapped case it would be 10dp.
    textView2.authoritativeNlComponent.let {
      assertEquals("16dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("16dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }
    assertEquals(constraintLayout.children[1], textView2)
  }

  fun testDragComponentInSameLayoutWillKeepOriginalPosition() {
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!

    myInteraction.select(textView2)
    myInteraction.mouseDown("textView2")
    myInteraction.mouseRelease(80f, 80f)

    textView2.authoritativeNlComponent.let {
      assertEquals("30dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
      assertEquals("30dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
    }
    assertEquals(constraintLayout.children[1], textView2)
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

    val linearLayout = myScreen.get("@id/linear").sceneComponent!!
    assertTrue(linearLayout.children.contains(textView))
    assertTrue(linearLayout.children.contains(textView2))

    assertEquals(2, myScreen.screen.selectionModel.selection.size)
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

  fun testAutoConnectionOnInConstraintLayout() {
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!
    val textView = myScreen.get("@id/textView").sceneComponent!!

    val autoconnected = ToggleAutoConnectAction.isAutoconnectOn()
    setAutoConnection(myScreen.screen, textView, true)

    myInteraction.select(textView)
    myInteraction.mouseDown("textView")
    myInteraction.mouseRelease((constraintLayout.drawX + constraintLayout.drawWidth / 2).toFloat(),
                               (constraintLayout.drawY + constraintLayout.drawHeight / 2).toFloat())

    val nlComponent = textView.nlComponent
    assertEquals(SdkConstants.ATTR_PARENT, nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF))
    assertEquals(SdkConstants.ATTR_PARENT, nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF))
    assertEquals(SdkConstants.ATTR_PARENT, nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
    assertEquals(SdkConstants.ATTR_PARENT, nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF))

    // Restore to original setting of auto-connect
    setAutoConnection(myScreen.screen, textView, autoconnected)
  }

  fun testAutoConnectionOffInConstraintLayout() {
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!
    val textView = myScreen.get("@id/textView").sceneComponent!!

    val autoconnected = ToggleAutoConnectAction.isAutoconnectOn()
    setAutoConnection(myScreen.screen, textView, false)

    myInteraction.select(textView)
    myInteraction.mouseDown("textView")
    myInteraction.mouseRelease((constraintLayout.drawX + constraintLayout.drawWidth / 2).toFloat(),
                               (constraintLayout.drawY + constraintLayout.drawHeight / 2).toFloat())

    val nlComponent = textView.nlComponent
    assertEquals("450dp", nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
    assertEquals("450dp", nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))

    // Restore to original setting of auto-connect
    setAutoConnection(myScreen.screen, textView, autoconnected)
  }

  fun testDragComponentInConstraintLayout() {
    // Regression test for b/123758530 and b/129681462

    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!
    val textView = myScreen.get("@id/textView").sceneComponent!!

    val x = textView.drawX
    val y = textView.drawY

    myInteraction.mouseDown("textView")
    myInteraction.mouseDrag((constraintLayout.drawX + constraintLayout.drawWidth / 2).toFloat(),
                            (constraintLayout.drawY + constraintLayout.drawHeight / 2).toFloat())

    assertTrue(x == textView.drawX)
    assertTrue(y == textView.drawY)
  }

  fun testTransactionSucceedWhenReleasingMouseAtLegalPositionWithLiveRendering() {
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!
    val textView = myScreen.get("@id/textView").sceneComponent!!

    constraintLayout.scene.isLiveRenderingEnabled = true

    val transaction = textView.authoritativeNlComponent.startAttributeTransaction()

    myInteraction.mouseDown("textView")
    myInteraction.mouseDrag((constraintLayout.drawX + constraintLayout.drawWidth / 2).toFloat(),
                            (constraintLayout.drawY + constraintLayout.drawHeight / 2).toFloat())
    myInteraction.mouseRelease((constraintLayout.drawX + constraintLayout.drawWidth / 2).toFloat(),
                               (constraintLayout.drawY + constraintLayout.drawHeight / 2).toFloat())

    assertTrue(transaction.isSuccessful)
  }

  fun testTransactionRollbackWhenReleasingMouseAtIllegalPositionWithLiveRendering() {
    val constraintLayout = myScreen.get("@id/constraint").sceneComponent!!
    val textView = myScreen.get("@id/textView").sceneComponent!!

    constraintLayout.scene.isLiveRenderingEnabled = true

    val transaction = textView.authoritativeNlComponent.startAttributeTransaction()

    myInteraction.mouseDown("textView")
    myInteraction.mouseDrag((constraintLayout.drawX - constraintLayout.drawWidth).toFloat(),
                            (constraintLayout.drawY - constraintLayout.drawHeight).toFloat())
    myInteraction.mouseRelease((constraintLayout.drawX - constraintLayout.drawWidth).toFloat(),
                               (constraintLayout.drawY - constraintLayout.drawHeight).toFloat())

    assertFalse(transaction.isSuccessful)
  }

  fun testFinalSelection() {
    val textView = myScreen.get("@id/textView").sceneComponent!!
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!

    val target = CommonDragTarget(textView)
    val target2 = CommonDragTarget(textView2)

    run {
      // Click textView should select only textView
      myScene.select(listOf(textView, textView2))
      target.mouseDown(10, 10)
      target.mouseRelease(11, 10, listOf())
      val newSelection = target.newSelection()
      assertEquals(1, newSelection.size)
      assertEquals(textView, newSelection[0])
    }

    run {
      // Click textView2 should select only textView2
      myScene.select(listOf(textView, textView2))
      target2.mouseDown(10, 10)
      target2.mouseRelease(11, 10, listOf())
      val newSelection = target2.newSelection()
      assertEquals(1, newSelection.size)
      assertEquals(textView2, newSelection[0])
    }

    run {
      // Drag textView should select both
      myScene.select(listOf(textView, textView2))
      target.mouseDown(10, 10)
      target.mouseDrag(11, 11, listOf(), SceneContext.get())
      target.mouseRelease(12, 12, listOf())
      val newSelection = target.newSelection()
      assertEquals(2, newSelection.size)
      assertEquals(textView, newSelection[0])
      assertEquals(textView2, newSelection[1])
    }

    // Select 2
    run {
      // Drag textView2 should select both
      myScene.select(listOf(textView, textView2))
      target2.mouseDown(10, 10)
      target2.mouseDrag(11, 11, listOf(), SceneContext.get())
      target2.mouseRelease(12, 12, listOf())
      val newSelection = target2.newSelection()
      assertEquals(2, newSelection.size)
      assertEquals(textView2, newSelection[0])
      assertEquals(textView, newSelection[1])
    }
  }

  fun testMouseCursor() {
    val textView = myScreen.get("@id/textView").sceneComponent!!

    run {
      // If component is not selected, the ALT_MASK doesn't change the cursor.
      myScene.select(listOf())
      val target = CommonDragTarget(textView)
      assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), target.getMouseCursor(0))
      assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), target.getMouseCursor(InputEvent.ALT_DOWN_MASK))
    }

    run {
      // If component is selected, the ALT_MASK changes the cursor.
      myScene.select(listOf(textView))
      val target = CommonDragTarget(textView)
      assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), target.getMouseCursor(0))
      assertEquals(AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE), target.getMouseCursor(InputEvent.ALT_DOWN_MASK))
    }
  }

  private fun setAutoConnection(view: SceneView, component: SceneComponent, on: Boolean) {
    if (ToggleAutoConnectAction.isAutoconnectOn() != on) {
      ToggleAutoConnectAction().perform(ViewEditorImpl(view),
                                        component.nlComponent.layoutHandler!!,
                                        component.parent!!.nlComponent,
                                        listOf(),
                                        0)
    }
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
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
                       .withBounds(1400, 1400, 600, 600)
                       .id("@id/linear")
                       .width("300dp")
                       .height("300dp")
                       .withAttribute(SdkConstants.ATTR_ORIENTATION, SdkConstants.VALUE_VERTICAL)
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "700dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "700dp")
                       .children(
                         component(SdkConstants.BUTTON)
                           .withBounds(1400, 1400, 200, 200)
                           .id("@id/button")
                           .width("100dp")
                           .height("100dp"),
                         component(SdkConstants.BUTTON)
                           .withBounds(1400, 1600, 200, 200)
                           .id("@id/button2")
                           .width("100dp")
                           .height("100dp")
                       )
                   )
    )
  }
}
