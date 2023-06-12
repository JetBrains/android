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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.LayoutTestUtilities
import org.intellij.lang.annotations.JdkConstants
import org.mockito.Mockito
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

open class MouseEventBuilder(@SwingCoordinate private val myX: Int, @SwingCoordinate private val myY: Int) {
  private var mySource: Any = LayoutTestUtilities::class.java
  private var myComponent: Component? = null
  private var myButton = 1
  private var myMask = 0
  private var myClickCount = 1
  private var myId = 0

  private var screenLocationX: Int = myX
  private var screenLocationY: Int = myY

  open fun withSource(source: Any): MouseEventBuilder {
    mySource = source
    return this
  }

  open fun withComponent(component: Component?): MouseEventBuilder = this.apply { myComponent = component }

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

  /**
   * Set the value of [MouseEvent.getLocationOnScreen]. If this is not set, then [myX] and [myY] is used by default.
   */
  open fun withLocationOnScreen(x: Int, y: Int) {
    screenLocationX = x
    screenLocationY = y
  }

  open fun build(): MouseEvent {
    return createMockEvent(MouseEvent::class.java);
  }

  protected fun <T: MouseEvent, U: Class<out T>> createMockEvent(clazz: U): T {
    val event = Mockito.mock(clazz)
    whenever(event.source).thenReturn(mySource)
    whenever(event.component).thenReturn(myComponent)
    whenever(event.x).thenReturn(myX)
    whenever(event.y).thenReturn(myY)
    whenever(event.point).thenReturn(Point(myX, myY))
    whenever(event.xOnScreen).thenReturn(screenLocationX)
    whenever(event.yOnScreen).thenReturn(screenLocationY)
    whenever(event.locationOnScreen).thenReturn(Point(screenLocationX, screenLocationY))
    whenever(event.modifiers).thenReturn(myMask)
    whenever(event.modifiersEx).thenReturn(myMask)
    whenever(event.button).thenReturn(myButton)
    whenever(event.clickCount).thenReturn(myClickCount)
    whenever(event.getWhen()).thenReturn(System.currentTimeMillis())
    whenever(event.id).thenReturn(myId)
    return event
  }
}
