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

import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.common.scene.draw.DrawCircle
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawFilledRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.intellij.ui.JBColor
import junit.framework.TestCase
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB

class SerializationTest : TestCase() {
  fun testDrawIcon() {
    val factory = { s: String -> DrawIcon(s) }

    testSerialization("DrawIcon,10.0x20.0x100.0x200.0,DEEPLINK,null",
                      DrawIcon(Rectangle2D.Float(10f, 20f, 100f, 200f),
                               DrawIcon.IconType.DEEPLINK), factory)
    testSerialization("DrawIcon,20.0x10.0x200.0x100.0,START_DESTINATION,null",
                      DrawIcon(Rectangle2D.Float(20f, 10f, 200f, 100f),
                               DrawIcon.IconType.START_DESTINATION), factory)
    testSerialization("DrawIcon,20.0x10.0x200.0x100.0,POP_ACTION,ffff0000",
                      DrawIcon(Rectangle2D.Float(20f, 10f, 200f, 100f),
                               DrawIcon.IconType.POP_ACTION, Color.RED), factory)
  }

  fun testDrawAction() {
    val factory = { s: String -> DrawAction(s) }

    testSerialization("DrawAction,REGULAR,10.0x20.0x30.0x40.0,50.0x60.0x70.0x80.0,ffffffff", DrawAction(
      ActionType.REGULAR,
      Rectangle2D.Float(10f, 20f, 30f, 40f),
      Rectangle2D.Float(50f, 60f, 70f, 80f),
      JBColor.WHITE), factory)
  }

  fun testDrawTruncatedText() {
    val factory = { s: String -> DrawTruncatedText(s) }

    testSerialization("DrawTruncatedText,0,foo,10.0x20.0x30.0x40.0,ffff0000,Default:0:10,true",
        DrawTruncatedText(0, "foo", Rectangle2D.Float(10f, 20f, 30f, 40f), Color.RED,
                          Font("Default", Font.PLAIN, 10), true), factory)

    testSerialization("DrawTruncatedText,1,bar,50.0x60.0x70.0x80.0,ff0000ff,Helvetica:1:20,false",
        DrawTruncatedText(1, "bar", Rectangle2D.Float(50f, 60f, 70f, 80f), Color.BLUE,
                          Font("Helvetica", Font.BOLD, 20), false), factory)
  }

  fun testDrawRectangle() {
    val factory = { s: String -> DrawRectangle(s) }

    testSerialization("DrawRectangle,0,10.0x20.0x30.0x40.0,ffff0000,1.0",
                      DrawRectangle(0, Rectangle2D.Float(10f, 20f, 30f, 40f),
                                         Color.RED, 1f), factory)

    testSerialization("DrawRectangle,1,50.0x60.0x70.0x80.0,ff0000ff,3.0",
                      DrawRectangle(1, Rectangle2D.Float(50f, 60f, 70f, 80f),
                                         Color.BLUE, 3f), factory)
  }

  fun testDrawFilledRectangle() {
    val factory = { s: String -> DrawFilledRectangle(s) }

    testSerialization("DrawFilledRectangle,0,10.0x20.0x30.0x40.0,ffff0000",
                      DrawFilledRectangle(0, Rectangle2D.Float(10f, 20f, 30f, 40f),
                                               Color.RED), factory)

    testSerialization("DrawFilledRectangle,1,50.0x60.0x70.0x80.0,ff0000ff",
                      DrawFilledRectangle(1, Rectangle2D.Float(50f, 60f, 70f, 80f),
                                               Color.BLUE), factory)
  }

  fun testDrawRoundRectangle() {
    val factory = { s: String -> DrawRoundRectangle(s) }

    testSerialization("DrawRoundRectangle,0,10.0x20.0x30.0x40.0x0.0x0.0,ffff0000,1.0",
                      DrawRoundRectangle(0, RoundRectangle2D.Float(10f, 20f, 30f, 40f, 0f, 0f),
                                         Color.RED, 1f), factory)

    testSerialization("DrawRoundRectangle,1,50.0x60.0x70.0x80.0x4.0x4.0,ff0000ff,3.0",
                      DrawRoundRectangle(1, RoundRectangle2D.Float(50f, 60f, 70f, 80f, 4f, 4f),
                                         Color.BLUE, 3f), factory)
  }

  fun testDrawFilledRoundRectangle() {
    val factory = { s: String -> DrawFilledRoundRectangle(s) }

    testSerialization("DrawFilledRoundRectangle,0,10.0x20.0x30.0x40.0x1.0x2.0,ffff0000",
                      DrawFilledRoundRectangle(0, RoundRectangle2D.Float(10f, 20f, 30f, 40f, 1f, 2f),
                                               Color.RED), factory)

    testSerialization("DrawFilledRoundRectangle,1,50.0x60.0x70.0x80.0x3.0x4.0,ff0000ff",
                      DrawFilledRoundRectangle(1, RoundRectangle2D.Float(50f, 60f, 70f, 80f, 3f, 4f),
                                               Color.BLUE), factory)
  }

  fun testDrawCircle() {
    val factory = { s: String -> DrawCircle(s) }

    testSerialization("DrawCircle,0,10.0x20.0,ffff0000,1,1.0:2.0:3",
                      DrawCircle(0, Point2D.Float(10f, 20f), Color.RED, BasicStroke(1F), LerpFloat(1f, 2f, 3)), factory)
    testSerialization("DrawCircle,1,30.0x40.0,ff0000ff,2,4.0:5.0:6",
        DrawCircle(1, Point2D.Float(30f, 40f), Color.BLUE, BasicStroke(2F), LerpFloat(4f, 5f, 6)), factory)
  }

  fun testDrawFilledCircle() {
    val factory = { s: String -> DrawFilledCircle(s) }

    testSerialization("DrawFilledCircle,0,10.0x20.0,ffff0000,1.0:2.0:3",
                      DrawFilledCircle(0, Point2D.Float(10f, 20f), Color.RED, LerpFloat(1f, 2f, 3)), factory)
    testSerialization("DrawFilledCircle,1,30.0x40.0,ff0000ff,4.0:5.0:6",
        DrawFilledCircle(1, Point2D.Float(30f, 40f), Color.BLUE, LerpFloat(4f, 5f, 6)), factory)
  }

  fun testDrawArrow() {
    val factory = { s: String -> DrawArrow(s) }

    testSerialization("DrawArrow,0,RIGHT,10.0x20.0x30.0x40.0,ffff0000",
                      DrawArrow(0, ArrowDirection.RIGHT, Rectangle2D.Float(10f, 20f, 30f, 40f), Color.RED), factory)
    testSerialization("DrawArrow,1,UP,60.0x70.0x80.0x90.0,ff0000ff",
                      DrawArrow(1, ArrowDirection.UP, Rectangle2D.Float(60f, 70f, 80f, 90f), Color.BLUE), factory)
  }

  fun testDrawLine() {
    val factory = { s: String -> DrawLine(s) }

    testSerialization("DrawLine,0,10.0x20.0,30.0x40.0,ffff0000,1:0:1",
        DrawLine(0, Point2D.Float(10f, 20f), Point2D.Float(30f, 40f),
            Color.RED, BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)), factory)
    testSerialization("DrawLine,1,60.0x70.0,80.0x90.0,ffff0000,2:1:2",
        DrawLine(1, Point2D.Float(60f, 70f), Point2D.Float(80f, 90f),
            Color.RED, BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)), factory)
  }

  fun testDrawEmptyDesigner() {
    val factory = { s: String -> DrawEmptyDesigner(s) }

    testSerialization("DrawEmptyDesigner,0x0", DrawEmptyDesigner(Point(0, 0)), factory)
    testSerialization("DrawEmptyDesigner,10x20", DrawEmptyDesigner(Point(10, 20)), factory)
  }

  fun testDrawNavScreen() {
    // Unfortunately the serialization doesn't include the actual image, so we'll always deserialize as "preview unavailable"
    val factory = { s: String -> DrawNavScreen(s) }

    testSerialization("DrawNavScreen,10.0x20.0x30.0x40.0",
                      DrawNavScreen(Rectangle2D.Float(10f, 20f, 30f, 40f),
                                    RefinableImage()), factory)
    testSerialization("DrawNavScreen,10.0x20.0x30.0x40.0",
                      DrawNavScreen(Rectangle2D.Float(10f, 20f, 30f, 40f),
                                    RefinableImage(BufferedImage(1, 1, TYPE_INT_RGB))), factory)
  }

  fun testDrawSelfAction() {
    val factory = { s: String -> DrawSelfAction(s) }

    testSerialization("DrawSelfAction,10.0x20.0,30.0x40.0,ffff0000",
                      DrawSelfAction(Point2D.Float(10f, 20f), Point2D.Float(30f, 40f), Color.RED), factory)
    testSerialization("DrawSelfAction,50.0x60.0,70.0x80.0,ff0000ff",
                      DrawSelfAction(Point2D.Float(50f, 60f), Point2D.Float(70f, 80f), Color.BLUE), factory)
  }

  fun testDrawPlaceholder() {
    val factory = { s: String -> DrawPlaceholder(s) }

    testSerialization("DrawPlaceholder,0,10.0x20.0x30.0x40.0",
                      DrawPlaceholder(0, Rectangle2D.Float(10f, 20f, 30f, 40f)), factory)
    testSerialization("DrawPlaceholder,1,50.0x60.0x70.0x80.0",
                      DrawPlaceholder(1, Rectangle2D.Float(50f, 60f, 70f, 80f)), factory)
  }

  fun testDrawActionHandle() {
    val factory = { s: String -> DrawActionHandle(s) }

    testSerialization("DrawActionHandle,0,10.0x20.0,1.0,2.0,3.0,4.0,5,ffff0000,ff0000ff",
                      DrawActionHandle(0, Point2D.Float(10f, 20f),
                                       1f, 2f, 3f, 4f, 5, Color.RED, Color.BLUE), factory)

    testSerialization("DrawActionHandle,0,30.0x40.0,11.0,12.0,13.0,14.0,15,ff00ff00,ffffc800",
                      DrawActionHandle(0, Point2D.Float(30f, 40f),
                                       11f, 12f, 13f, 14f, 15, Color.GREEN, Color.ORANGE), factory)
  }

  fun testDrawActionHandleDrag() {
    val factory = { s: String -> DrawActionHandleDrag(s) }

    testSerialization("DrawActionHandleDrag,0,10.0x20.0,1.0,2.0,3.0,4", DrawActionHandleDrag(0, Point2D.Float(10f, 20f),
                                                                         1f, 2f, 3f, 4), factory)
    testSerialization("DrawActionHandleDrag,1,30.0x40.0,11.0,12.0,13.0,4", DrawActionHandleDrag(1, Point2D.Float(30f, 40f),
                                                                         11f, 12f, 13f, 4), factory)
  }

  fun testDrawHorizontalAction() {
    val factory = { s: String -> DrawHorizontalAction(s) }

    testSerialization("DrawHorizontalAction,0,10.0x20.0x30.0x40.0,ffff0000,false",
                      DrawHorizontalAction(0, Rectangle2D.Float(10f, 20f, 30f, 40f), Color.RED, false), factory)

    testSerialization("DrawHorizontalAction,1,50.0x60.0x70.0x80.0,ff0000ff,true",
                      DrawHorizontalAction(1, Rectangle2D.Float(50f, 60f, 70f, 80f), Color.BLUE, true), factory)
  }

  fun testDrawLineToMouse() {
    val factory = { s: String -> DrawLineToMouse(s) }

    testSerialization("DrawLineToMouse,0,10.0x20.0", DrawLineToMouse(0, Point2D.Float(10f, 20f)), factory)
    testSerialization("DrawLineToMouse,0,30.0x40.0", DrawLineToMouse(0, Point2D.Float(30f, 40f)), factory)
  }

  fun testDrawNestedGraph() {
    val factory = { s: String -> DrawNestedGraph(s) }

    testSerialization("DrawNestedGraph,10.0x20.0x30.0x40.0,1.5,ffff0000,1.0,text1,ff0000ff", DrawNestedGraph(
      Rectangle2D.Float(10f, 20f, 30f, 40f), 1.5f, Color.RED, 1f, "text1", Color.BLUE), factory)
    testSerialization("DrawNestedGraph,50.0x60.0x70.0x80.0,0.5,ffffffff,2.0,text2,ff000000", DrawNestedGraph(
      Rectangle2D.Float(50f, 60f, 70f, 80f), 0.5f, Color.WHITE, 2f, "text2", Color.BLACK), factory)
  }

  fun testDrawFragment() {
    val factory = { s: String -> DrawFragment(s) }

    testSerialization("DrawFragment,10.0x20.0x30.0x40.0,1.5,null", DrawFragment(
      Rectangle2D.Float(10f, 20f, 30f, 40f), 1.5f, null), factory)
    testSerialization("DrawFragment,50.0x60.0x70.0x80.0,0.5,ffffffff", DrawFragment(
      Rectangle2D.Float(50f, 60f, 70f, 80f), 0.5f, Color.WHITE), factory)
  }

  companion object {
    private fun testSerialization(s: String, drawCommand: DrawCommand, factory: (String) -> DrawCommand) {
      val serialized = drawCommand.serialize()
      TestCase.assertEquals(s, serialized)

      val deserialized = factory(serialized.substringAfter(','))
      TestCase.assertEquals(serialized, deserialized.serialize())
    }
  }
}
