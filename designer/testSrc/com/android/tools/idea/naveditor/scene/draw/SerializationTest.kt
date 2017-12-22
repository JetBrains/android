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

import com.android.tools.idea.common.scene.LerpValue
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import junit.framework.TestCase
import java.awt.*

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

    testSerialization("DrawAction,NORMAL,10x20x30x40,50x60x70x80,HOVER", DrawAction(ActionTarget.ConnectionType.NORMAL,
        Rectangle(10, 20, 30, 40),
        Rectangle(50, 60, 70, 80),
        DrawAction.DrawMode.HOVER), factory)

    testSerialization("DrawAction,SELF,80x70x60x50,40x30x20x10,SELECTED", DrawAction(ActionTarget.ConnectionType.SELF,
        Rectangle(80, 70, 60, 50),
        Rectangle(40, 30, 20, 10),
        DrawAction.DrawMode.SELECTED), factory)
  }

  fun testDrawActionHandleDrag() {
    val factory = { s: String -> DrawActionHandleDrag(s) }

    testSerialization("DrawActionHandleDrag,10,20,5", DrawActionHandleDrag(10, 20, 5), factory)
    testSerialization("DrawActionHandleDrag,30,50,10", DrawActionHandleDrag(30, 50, 10), factory)
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

    testSerialization("DrawRectangle,10x20x30x40,ffff0000,1,0",
        DrawRectangle(Rectangle(10, 20, 30, 40),
            Color.RED, 1), factory)

    testSerialization("DrawRectangle,50x60x70x80,ff0000ff,3,4",
        DrawRectangle(Rectangle(50, 60, 70, 80),
            Color.BLUE, 3, 4), factory)
  }

  fun testDrawFilledRectangle() {
    val factory = { s: String -> DrawFilledRectangle(s) }

    testSerialization("DrawFilledRectangle,10x20x30x40,ffff0000,2",
        DrawFilledRectangle(Rectangle(10, 20, 30, 40),
            Color.RED, 2), factory)

    testSerialization("DrawFilledRectangle,50x60x70x80,ff0000ff,4",
        DrawFilledRectangle(Rectangle(50, 60, 70, 80),
            Color.BLUE, 4), factory)
  }

  fun testDrawCircle() {
    val factory = { s: String -> DrawCircle(s) }

    testSerialization("DrawCircle,0,10x20,ffff0000,1,1:2:3",
        DrawCircle(0, Point(10, 20), Color.RED, BasicStroke(1F), LerpValue(1, 2, 3)), factory)
    testSerialization("DrawCircle,1,30x40,ff0000ff,2,4:5:6",
        DrawCircle(1, Point(30, 40), Color.BLUE, BasicStroke(2F), LerpValue(4, 5, 6)), factory)
  }

  fun testDrawFilledCircle() {
    val factory = { s: String -> DrawFilledCircle(s) }

    testSerialization("DrawFilledCircle,0,10x20,ffff0000,1:2:3",
        DrawFilledCircle(0, Point(10, 20), Color.RED, LerpValue(1, 2, 3)), factory)
    testSerialization("DrawFilledCircle,1,30x40,ff0000ff,4:5:6",
        DrawFilledCircle(1, Point(30, 40), Color.BLUE, LerpValue(4, 5, 6)), factory)
  }

  fun testDrawArrow() {
    val factory = { s: String -> DrawArrow(s) }

    testSerialization("DrawArrow,0,RIGHT,10x20x30x40,ffff0000",
        DrawArrow(0, ArrowDirection.RIGHT, Rectangle(10, 20, 30, 40), Color.RED), factory)
    testSerialization("DrawArrow,1,UP,60x70x80x90,ff0000ff",
        DrawArrow(1, ArrowDirection.UP, Rectangle(60, 70, 80, 90), Color.BLUE), factory)
  }

  fun testDrawLine() {
    val factory = { s: String -> DrawLine(s) }

    testSerialization("DrawLine,0,10x20,30x40,ffff0000,1:0:1",
        DrawLine(0, Point(10, 20), Point(30, 40),
            Color.RED, BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)), factory)
    testSerialization("DrawLine,1,60x70,80x90,ffff0000,2:1:2",
        DrawLine(1, Point(60, 70), Point(80, 90),
            Color.RED, BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)), factory)
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
