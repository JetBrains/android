/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.fixtures.MouseEventBuilder
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JPanel
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesignSurfaceHelperTest {

  @Test
  fun testAutoHiddenListener() {
    val owner = mock<JPanel>()
    val pane = JPanel(BorderLayout())

    whenever(owner.locationOnScreen).thenReturn(Point(0, 0))
    whenever(owner.visibleRect).thenReturn(Rectangle(100, 100))
    whenever(owner.isShowing).thenReturn(true)

    pane.isVisible = false
    val listener = createZoomControlAutoHiddenListener(owner, pane)
    listener.eventDispatched(MouseEventBuilder(10, 10).withId(MouseEvent.MOUSE_ENTERED).build())
    assertTrue(pane.isVisible)

    listener.eventDispatched(MouseEventBuilder(200, 200).withId(MouseEvent.MOUSE_ENTERED).build())
    assertFalse(pane.isVisible)

    pane.isVisible = true
    whenever(owner.isShowing).thenReturn(false)
    listener.eventDispatched(MouseEventBuilder(10, 10).withId(MouseEvent.MOUSE_ENTERED).build())
    assertFalse(pane.isVisible)
  }
}
