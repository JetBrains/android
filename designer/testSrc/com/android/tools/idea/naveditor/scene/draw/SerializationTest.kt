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
import junit.framework.TestCase
import java.awt.Rectangle

class SerializationTest : TestCase() {
  fun testDrawIcon() {
    val factory = { s: String -> DrawIcon(s) }

    testSerialization("DrawIcon,23,10x20x100x200,DEEPLINK", DrawIcon(Rectangle(10, 20, 100, 200), DrawIcon.IconType.DEEPLINK), factory)
    testSerialization("DrawIcon,23,20x10x200x100,START_DESTINATION", DrawIcon(Rectangle(20, 10, 200, 100), DrawIcon.IconType.START_DESTINATION), factory)
  }

  fun testDrawNavigationFrame() {
    val factory = { s: String -> DrawNavigationFrame(s) }

    testSerialization("DrawNavigationFrame,20,10x20x100x200,true,false", DrawNavigationFrame(Rectangle(10, 20, 100, 200), true, false), factory)
    testSerialization("DrawNavigationFrame,20,20x10x200x100,false,true", DrawNavigationFrame(Rectangle(20, 10, 200, 100), false, true), factory)
  }

  fun testDrawFragmentFrame() {
    val factory = { s: String -> DrawScreenFrame(s) }

    testSerialization("DrawScreenFrame,20,10x20x100x200,true,false", DrawScreenFrame(Rectangle(10, 20, 100, 200), true, false), factory)
    testSerialization("DrawScreenFrame,20,20x10x200x100,false,true", DrawScreenFrame(Rectangle(20, 10, 200, 100), false, true), factory)
  }

  fun testDrawNavigationBackground() {
    val factory = { s: String -> DrawNavigationBackground(s) }

    testSerialization("DrawNavigationBackground,20,10x20x100x200", DrawNavigationBackground(Rectangle(10, 20, 100, 200)), factory)
    testSerialization("DrawNavigationBackground,20,20x10x200x100", DrawNavigationBackground(Rectangle(20, 10, 200, 100)), factory)
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
