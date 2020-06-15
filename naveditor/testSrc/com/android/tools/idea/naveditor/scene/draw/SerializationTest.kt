/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingFont
import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.intellij.ui.JBColor
import junit.framework.TestCase
import java.awt.Color
import java.awt.Font
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB

class SerializationTest : TestCase() {
  fun testDrawIcon() {
    val factory = { s: String -> DrawIcon(s) }

    testSerialization("DrawIcon,10.0x20.0x100.0x200.0,DEEPLINK,null",
                      DrawIcon(SwingRectangle(Rectangle2D.Float(10f, 20f, 100f, 200f)),
                               DrawIcon.IconType.DEEPLINK), factory)
    testSerialization("DrawIcon,20.0x10.0x200.0x100.0,START_DESTINATION,null",
                      DrawIcon(SwingRectangle(Rectangle2D.Float(20f, 10f, 200f, 100f)),
                               DrawIcon.IconType.START_DESTINATION), factory)
    testSerialization("DrawIcon,20.0x10.0x200.0x100.0,POP_ACTION,ffff0000",
                      DrawIcon(SwingRectangle(Rectangle2D.Float(20f, 10f, 200f, 100f)),
                               DrawIcon.IconType.POP_ACTION, Color.RED), factory)
  }

  fun testDrawAction() {
    val factory = { s: String -> DrawAction(s) }

    testSerialization("DrawAction,10.0x20.0x30.0x40.0,50.0x60.0x70.0x80.0,1.0,ffffffff,false", DrawAction(
      SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)),
      SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(1.0),
      JBColor.WHITE, false), factory)
  }

  fun testDrawTruncatedText() {
    val factory = { s: String -> DrawTruncatedText(s) }

    testSerialization("DrawTruncatedText,0,foo,10.0x20.0x30.0x40.0,ffff0000,Default:0:10,true",
                      DrawTruncatedText(0, "foo", SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Color.RED,
                                        SwingFont("Default", Font.PLAIN, SwingLength(10f)), true), factory)

    testSerialization("DrawTruncatedText,1,bar,50.0x60.0x70.0x80.0,ff0000ff,Helvetica:1:20,false",
                      DrawTruncatedText(1, "bar", SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Color.BLUE,
                                        SwingFont("Helvetica", Font.BOLD, SwingLength(20f)), false), factory)
  }

  fun testDrawEmptyDesigner() {
    val factory = { s: String -> DrawEmptyDesigner(s) }

    testSerialization("DrawEmptyDesigner,0.0x0.0", DrawEmptyDesigner(SwingPoint(Point2D.Float(0f, 0f))), factory)
    testSerialization("DrawEmptyDesigner,10.0x20.0", DrawEmptyDesigner(SwingPoint(Point2D.Float(10f, 20f))), factory)
  }

  fun testDrawNavScreen() {
    // Unfortunately the serialization doesn't include the actual image, so we'll always deserialize as "preview unavailable"
    val factory = { s: String -> DrawNavScreen(s) }

    testSerialization("DrawNavScreen,10.0x20.0x30.0x40.0",
                      DrawNavScreen(SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)),
                                    RefinableImage()), factory)
    testSerialization("DrawNavScreen,10.0x20.0x30.0x40.0",
                      DrawNavScreen(SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)),
                                    RefinableImage(BufferedImage(1, 1, TYPE_INT_RGB))), factory)
  }

  fun testDrawSelfAction() {
    val factory = { s: String -> DrawSelfAction(s) }

    testSerialization("DrawSelfAction,10.0x20.0x30.0x40.0,1.0,ffff0000,false",
                      DrawSelfAction(SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Scale(1.0), Color.RED, false), factory)
    testSerialization("DrawSelfAction,50.0x60.0x70.0x80.0,2.0,ff0000ff,true",
                      DrawSelfAction(SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(2.0), Color.BLUE, true), factory)
  }

  fun testDrawActionHandle() {
    val factory = { s: String -> DrawActionHandle(s) }

    testSerialization("DrawActionHandle,10.0x20.0,1.0,2.0,3.0,4.0,5,ffff0000,ff0000ff",
                      DrawActionHandle(SwingPoint(Point2D.Float(10f, 20f)),
                                       SwingLength(1f), SwingLength(2f), SwingLength(3f), SwingLength(4f),
                                       5, Color.RED, Color.BLUE), factory)

    testSerialization("DrawActionHandle,30.0x40.0,11.0,12.0,13.0,14.0,15,ff00ff00,ffffc800",
                      DrawActionHandle(SwingPoint(Point2D.Float(30f, 40f)),
                                       SwingLength(11f), SwingLength(12f), SwingLength(13f), SwingLength(14f),
                                       15, Color.GREEN, Color.ORANGE), factory)
  }

  fun testDrawActionHandleDrag() {
    val factory = { s: String -> DrawActionHandleDrag(s) }

    testSerialization("DrawActionHandleDrag,10.0x20.0,1.0,2.0,3.0,4",
                      DrawActionHandleDrag(SwingPoint(Point2D.Float(10f, 20f)),
                                           SwingLength(1f), SwingLength(2f), SwingLength(3f), 4), factory)
    testSerialization("DrawActionHandleDrag,30.0x40.0,11.0,12.0,13.0,4", DrawActionHandleDrag(SwingPoint(Point2D.Float(30f, 40f)),
                                                                                              SwingLength(11f), SwingLength(12f),
                                                                                              SwingLength(13f), 4), factory)
  }

  fun testDrawHorizontalAction() {
    val factory = { s: String -> DrawHorizontalAction(s) }

    testSerialization("DrawHorizontalAction,10.0x20.0x30.0x40.0,1.0,ffff0000,false",
                      DrawHorizontalAction(SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Scale(1.0), Color.RED, false), factory)

    testSerialization("DrawHorizontalAction,50.0x60.0x70.0x80.0,2.0,ff0000ff,true",
                      DrawHorizontalAction(SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(2.0), Color.BLUE, true), factory)
  }

  fun testDrawLineToMouse() {
    val factory = { s: String -> DrawLineToMouse(s) }

    testSerialization("DrawLineToMouse,10.0x20.0", DrawLineToMouse(SwingPoint(Point2D.Float(10f, 20f))), factory)
    testSerialization("DrawLineToMouse,30.0x40.0", DrawLineToMouse(SwingPoint(Point2D.Float(30f, 40f))), factory)
  }

  fun testDrawNestedGraph() {
    val factory = { s: String -> DrawNestedGraph(s) }

    testSerialization("DrawNestedGraph,10.0x20.0x30.0x40.0,1.5,ffff0000,1.0,text1,ff0000ff", DrawNestedGraph(
      SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Scale(1.5), Color.RED, SwingLength(1f), "text1", Color.BLUE), factory)
    testSerialization("DrawNestedGraph,50.0x60.0x70.0x80.0,0.5,ffffffff,2.0,text2,ff000000", DrawNestedGraph(
      SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(0.5), Color.WHITE, SwingLength(2f), "text2", Color.BLACK), factory)
  }

  fun testDrawFragment() {
    val factory = { s: String -> DrawFragment(s) }

    testSerialization("DrawFragment,10.0x20.0x30.0x40.0,1.5,null", DrawFragment(
      SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Scale(1.5), null), factory)
    testSerialization("DrawFragment,50.0x60.0x70.0x80.0,0.5,ffffffff", DrawFragment(
      SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(0.5), Color.WHITE), factory)
  }

  fun testDrawActivity() {
    val factory = { s: String -> DrawActivity(s) }

    testSerialization("DrawActivity,10.0x20.0x30.0x40.0,15.0x25.0x35.0x45.0,1.5,ffff0000,1.0,ff0000ff", DrawActivity(
      SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), SwingRectangle(Rectangle2D.Float(15f, 25f, 35f, 45f)),
      Scale(1.5), Color.RED, SwingLength(1f), Color.BLUE), factory)
    testSerialization("DrawActivity,50.0x60.0x70.0x80.0,55.0x65.0x75.0x85.0,0.5,ffffffff,2.0,ff000000", DrawActivity(
      SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), SwingRectangle(Rectangle2D.Float(55f, 65f, 75f, 85f)),
      Scale(0.5), Color.WHITE, SwingLength(2f), Color.BLACK), factory)
  }

  fun testDrawHeader() {
    val factory = { s: String -> DrawHeader(s) }

    testSerialization("DrawHeader,10.0x20.0x30.0x40.0,1.5,text1,true,false", DrawHeader(
      SwingRectangle(Rectangle2D.Float(10f, 20f, 30f, 40f)), Scale(1.5), "text1", true, false), factory)
    testSerialization("DrawHeader,50.0x60.0x70.0x80.0,0.5,text2,false,true", DrawHeader(
      SwingRectangle(Rectangle2D.Float(50f, 60f, 70f, 80f)), Scale(0.5), "text2", false, true), factory)
  }

  companion object {
    private fun testSerialization(s: String, drawCommand: DrawCommand, factory: (String) -> DrawCommand) {
      val serialized = drawCommand.serialize()
      assertEquals(s, serialized)

      val deserialized = factory(serialized.substringAfter(','))
      assertEquals(serialized, deserialized.serialize())
    }
  }
}
