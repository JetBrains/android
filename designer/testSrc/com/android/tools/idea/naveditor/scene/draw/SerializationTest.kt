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
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import junit.framework.TestCase
import java.awt.*
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.util.concurrent.CompletableFuture

class SerializationTest : TestCase() {
  fun testDrawIcon() {
    val factory = { s: String -> DrawIcon(s) }

    testSerialization("DrawIcon,10x20x100x200,DEEPLINK", DrawIcon(Rectangle(10, 20, 100, 200), DrawIcon.IconType.DEEPLINK), factory)
    testSerialization("DrawIcon,20x10x200x100,START_DESTINATION", DrawIcon(Rectangle(20, 10, 200, 100), DrawIcon.IconType.START_DESTINATION), factory)
  }

  fun testDrawAction() {
    val factory = { s: String -> DrawAction(s) }

    testSerialization("DrawAction,NORMAL,10x20x30x40,50x60x70x80,NORMAL", DrawAction(ActionTarget.ConnectionType.NORMAL,
        Rectangle(10, 20, 30, 40),
        Rectangle(50, 60, 70, 80),
        DrawAction.DrawMode.NORMAL), factory)

    testSerialization("DrawAction,EXIT,10x20x30x40,50x60x70x80,HOVER", DrawAction(ActionTarget.ConnectionType.EXIT,
        Rectangle(10, 20, 30, 40),
        Rectangle(50, 60, 70, 80),
        DrawAction.DrawMode.HOVER), factory)
  }

  fun testDrawActionHandleDrag() {
    val factory = { s: String -> DrawActionHandleDrag(s) }

    testSerialization("DrawActionHandleDrag,10,20", DrawActionHandleDrag(10, 20), factory)
    testSerialization("DrawActionHandleDrag,30,50", DrawActionHandleDrag(30, 50), factory)
  }

  fun testDrawTruncatedText() {
    val factory = { s: String -> DrawTruncatedText(s) }

    testSerialization("DrawTruncatedText,0,foo,10x20x30x40,ffff0000,Default:0:10,true",
        DrawTruncatedText(0, "foo", Rectangle(10, 20, 30, 40), Color.RED,
            Font("Default", Font.PLAIN, 10), true), factory)

    testSerialization("DrawTruncatedText,1,bar,50x60x70x80,ff0000ff,Helvetica:1:20,false",
        DrawTruncatedText(1, "bar", Rectangle(50, 60, 70, 80), Color.BLUE,
            Font("Helvetica", Font.BOLD, 20), false), factory)
  }

  fun testDrawRectangle() {
    val factory = { s: String -> DrawRectangle(s) }

    testSerialization("DrawRectangle,0,10.0x20.0x30.0x40.0x0.0x0.0,ffff0000,1.0",
                      DrawRectangle(0, RoundRectangle2D.Float(10f, 20f, 30f, 40f, 0f, 0f),
                                    Color.RED, 1f), factory)

    testSerialization("DrawRectangle,1,50.0x60.0x70.0x80.0x4.0x4.0,ff0000ff,3.0",
                      DrawRectangle(1, RoundRectangle2D.Float(50f, 60f, 70f, 80f, 4f, 4f),
                                    Color.BLUE, 3f), factory)
  }

  fun testDrawFilledRectangle() {
    val factory = { s: String -> DrawFilledRectangle(s) }

    testSerialization("DrawFilledRectangle,0,10.0x20.0x30.0x40.0x1.0x2.0,ffff0000",
                      DrawFilledRectangle(0, RoundRectangle2D.Float(10f, 20f, 30f, 40f, 1f, 2f),
                                          Color.RED), factory)

    testSerialization("DrawFilledRectangle,1,50.0x60.0x70.0x80.0x3.0x4.0,ff0000ff",
                      DrawFilledRectangle(1, RoundRectangle2D.Float(50f, 60f, 70f, 80f, 3f, 4f),
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

    testSerialization("DrawArrow,0,RIGHT,10.0x20.0x30.0x40.0x0.0x0.0,ffff0000",
                      DrawArrow(0, ArrowDirection.RIGHT, RoundRectangle2D.Float(10f, 20f, 30f, 40f, 0f, 0f), Color.RED), factory)
    testSerialization("DrawArrow,1,UP,60.0x70.0x80.0x90.0x0.0x0.0,ff0000ff",
                      DrawArrow(1, ArrowDirection.UP, RoundRectangle2D.Float(60f, 70f, 80f, 90f, 0f, 0f), Color.BLUE), factory)
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

    testSerialization("DrawNavScreen,10x20x30x40", DrawNavScreen(Rectangle(10, 20, 30, 40), CompletableFuture.completedFuture(null)),
                      factory)
    testSerialization("DrawNavScreen,10x20x30x40", DrawNavScreen(Rectangle(10, 20, 30, 40), CompletableFuture.completedFuture(
      BufferedImage(1, 1, TYPE_INT_RGB))), factory)
  }

  fun testDrawSelfAction() {
    val factory = { s: String -> DrawSelfAction(s) }

    testSerialization("DrawSelfAction,10x20,30x40,ffff0000", DrawSelfAction(Point(10, 20), Point(30, 40), Color.RED), factory)
    testSerialization("DrawSelfAction,50x60,70x80,ff0000ff", DrawSelfAction(Point(50, 60), Point(70, 80), Color.BLUE), factory)
  }

  companion object {
    private fun testSerialization(s: String, drawCommand: DrawCommand, factory: (String) -> DrawCommand) {
      val serialized = drawCommand.serialize()
      TestCase.assertEquals(serialized, s)

      val deserialized = factory(serialized.substringAfter(','))
      TestCase.assertEquals(serialized, deserialized.serialize())
    }
  }
}
