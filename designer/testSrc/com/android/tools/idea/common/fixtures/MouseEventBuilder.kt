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
package com.android.tools.idea.common.fixtures

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import org.intellij.lang.annotations.JdkConstants
import org.mockito.Mockito
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

open class MouseEventBuilder(@SwingCoordinate private val myX: Int, @SwingCoordinate private val myY: Int) {
  private var mySource: Any = LayoutTestUtilities::class.java
  private var myButton = 1
  private var myMask = 0
  private var myClickCount = 1
  private var myId = 0

  open fun withSource(source: Any): MouseEventBuilder {
    mySource = source
    return this
  }

  open fun withMask(@JdkConstants.InputEventMask mask: Int): MouseEventBuilder {
    myMask = mask
    return this
  }

  open fun withButton(button: Int): MouseEventBuilder {
    myButton = button
    // MOUSE_RELEASED doesn't have the button in the mask
    if (myId != MouseEvent.MOUSE_RELEASED) {
      myMask = myMask or InputEvent.getMaskForButton(button)
    }
    return this
  }

  open fun withClickCount(clickCount: Int): MouseEventBuilder {
    myClickCount = clickCount
    return this
  }

  open fun withId(id: Int): MouseEventBuilder {
    myId = id
    // MOUSE_RELEASED doesn't have the button in the mask
    if (id == MouseEvent.MOUSE_RELEASED) {
      myMask = myMask xor InputEvent.getMaskForButton(myButton)
    }
    return this
  }

  open fun build(): MouseEvent {
    return createMockEvent(MouseEvent::class.java);
  }

  protected fun <T : MouseEvent, U : Class<out T>> createMockEvent(clazz: U): T {
    val event = Mockito.mock(clazz)
    Mockito.`when`(event.source).thenReturn(mySource)
    Mockito.`when`(event.x).thenReturn(myX)
    Mockito.`when`(event.y).thenReturn(myY)
    Mockito.`when`(event.modifiers).thenReturn(myMask)
    Mockito.`when`(event.modifiersEx).thenReturn(myMask)
    Mockito.`when`(event.button).thenReturn(myButton)
    Mockito.`when`(event.clickCount).thenReturn(myClickCount)
    Mockito.`when`(event.point).thenReturn(Point(myX, myY))
    Mockito.`when`(event.getWhen()).thenReturn(System.currentTimeMillis())
    Mockito.`when`(event.id).thenReturn(myId)
    return event
  }
}
