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
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL
import org.mockito.kotlin.whenever

class MouseWheelEventBuilder(@SwingCoordinate x: Int, @SwingCoordinate y: Int) :
  MouseEventBuilder(x, y) {

  /** By default we assume it only scroll 1 tick. */
  private var scrollAmount: Int = 1
  private var scrollType: Int = WHEEL_UNIT_SCROLL
  private var unitsToScroll: Int = 1

  override fun withSource(source: Any) = super.withSource(source) as MouseWheelEventBuilder

  override fun withMask(mask: Int) = super.withMask(mask) as MouseWheelEventBuilder

  override fun withButton(button: Int) = super.withButton(button) as MouseWheelEventBuilder

  override fun withClickCount(clickCount: Int) =
    super.withClickCount(clickCount) as MouseWheelEventBuilder

  override fun withId(id: Int) = super.withId(id) as MouseWheelEventBuilder

  /** Set the amount (number of tick). By default it is 1 */
  fun withAmount(scrollAmount: Int) = apply { this.scrollAmount = scrollAmount }

  /** Set the type of scrolling, by default it is [WHEEL_UNIT_SCROLL] */
  fun withScrollType(scrollType: Int) = apply { this.scrollType = scrollType }

  /** Set the amount of one tick. By default it is 1 */
  fun withUnitToScroll(unitsToScroll: Int) = apply { this.unitsToScroll = unitsToScroll }

  override fun build(): MouseWheelEvent {
    val event = createMockEvent(MouseWheelEvent::class.java)
    whenever(event.scrollAmount).thenReturn(scrollAmount)
    whenever(event.scrollType).thenReturn(scrollType)
    whenever(event.unitsToScroll).thenReturn(unitsToScroll)
    return event
  }
}
