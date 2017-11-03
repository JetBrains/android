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

import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import junit.framework.TestCase
import java.awt.Rectangle

class SerializationTest : TestCase() {
  fun testDrawIcon() {
    val factory = { s: String -> DrawIcon(s) }

    testSerialization("DrawIcon,10x20x100x200,DEEPLINK", DrawIcon(Rectangle(10, 20, 100, 200), DrawIcon.IconType.DEEPLINK), factory)
    testSerialization("DrawIcon,20x10x200x100,START_DESTINATION", DrawIcon(Rectangle(20, 10, 200, 100), DrawIcon.IconType.START_DESTINATION), factory)
  }

  fun testDrawNavigationFrame() {
    val factory = { s: String -> DrawNavigationFrame(s) }

    testSerialization("DrawNavigationFrame,10x20x100x200,true,false", DrawNavigationFrame(Rectangle(10, 20, 100, 200), true, false), factory)
    testSerialization("DrawNavigationFrame,20x10x200x100,false,true", DrawNavigationFrame(Rectangle(20, 10, 200, 100), false, true), factory)
  }

  fun testDrawScreenFrame() {
    val factory = { s: String -> DrawScreenFrame(s) }

    testSerialization("DrawScreenFrame,10x20x100x200,true,false", DrawScreenFrame(Rectangle(10, 20, 100, 200), true, false), factory)
    testSerialization("DrawScreenFrame,20x10x200x100,false,true", DrawScreenFrame(Rectangle(20, 10, 200, 100), false, true), factory)
  }

  fun testDrawNavigationBackground() {
    val factory = { s: String -> DrawNavigationBackground(s) }

    testSerialization("DrawNavigationBackground,10x20x100x200", DrawNavigationBackground(Rectangle(10, 20, 100, 200)), factory)
    testSerialization("DrawNavigationBackground,20x10x200x100", DrawNavigationBackground(Rectangle(20, 10, 200, 100)), factory)
  }

  fun testDrawAction() {
    val factory = { s: String -> DrawAction(s) }

    testSerialization("DrawAction,NORMAL,10x20x30x40,50x60x70x80,NORMAL", DrawAction(ActionTarget.ConnectionType.NORMAL,
        Rectangle(10, 20, 30, 40),
        Rectangle(50, 60, 70, 80),
        DrawAction.DrawMode.NORMAL), factory)

    testSerialization("DrawAction,SELF,80x70x60x50,40x30x20x10,SELECTED", DrawAction(ActionTarget.ConnectionType.SELF,
        Rectangle(80, 70, 60, 50),
        Rectangle(40, 30, 20, 10),
        DrawAction.DrawMode.SELECTED), factory)
  }

  fun testDrawActionHandle() {
    val factory = { s: String -> DrawActionHandle(s) }

    testSerialization("DrawActionHandle,10,20,5,10,FRAMES,100", DrawActionHandle(10, 20, 5, 10, DrawColor.FRAMES, 100), factory)
    testSerialization("DrawActionHandle,20,40,10,5,SELECTED_FRAMES,200", DrawActionHandle(20, 40, 10, 5, DrawColor.SELECTED_FRAMES, 200), factory)
    testSerialization("DrawActionHandle,10,60,5,20,FRAMES,300", DrawActionHandle(10, 60, 5, 20, DrawColor.FRAMES, 300), factory)
    testSerialization("DrawActionHandle,20,80,5,10,SELECTED_FRAMES,400", DrawActionHandle(20, 80, 5, 10, DrawColor.SELECTED_FRAMES, 400), factory)
  }

  fun testDrawActionHandleDrag() {
    val factory = { s: String -> DrawActionHandleDrag(s) }

    testSerialization("DrawActionHandleDrag,10,20,5", DrawActionHandleDrag(10, 20, 5), factory)
    testSerialization("DrawActionHandleDrag,30,50,10", DrawActionHandleDrag(30, 50, 10), factory)
  }

  fun testDrawScreenLabel() {
    val factory = { s: String -> DrawScreenLabel(s) }

    testSerialization("DrawScreenLabel,10,20,foo", DrawScreenLabel(10, 20, "foo"), factory)
    testSerialization("DrawScreenLabel,30,40,bar", DrawScreenLabel(30, 40, "bar"), factory)
  }

  companion object {
    private fun testSerialization(s: String, drawCommand: DrawCommand, factory: (String) -> DrawCommand) {
      val serialized = drawCommand.serialize()
      TestCase.assertEquals(serialized, s)

      val deserialized = factory(serialized)
      TestCase.assertEquals(serialized, deserialized.serialize())
    }
  }
}
