/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.event.MouseEvent

class MaterialColorPaletteButtonTest {

  @Test
  fun testChangeStatusByMouseEvent() {
    val button = ColorButton()
    button.color = Color.BLUE
    button.setSize(10, 10)

    assertEquals(ColorButton.Status.NORMAL, button.status)

    button.dispatchEvent(MouseEvent(button,
                                    MouseEvent.MOUSE_ENTERED,
                                    System.currentTimeMillis(),
                                    0,
                                    0,
                                    0,
                                    1,
                                    false))
    assertEquals(ColorButton.Status.HOVER, button.status)

    button.dispatchEvent(MouseEvent(button,
                                    MouseEvent.MOUSE_PRESSED,
                                    System.currentTimeMillis(),
                                    0,
                                    5,
                                    5,
                                    1,
                                    false))
    assertEquals(ColorButton.Status.PRESSED, button.status)

    button.dispatchEvent(MouseEvent(button,
                                    MouseEvent.MOUSE_RELEASED,
                                    System.currentTimeMillis(),
                                    0,
                                    5,
                                    5,
                                    1,
                                    false))
    assertEquals(ColorButton.Status.HOVER, button.status)

    button.dispatchEvent(MouseEvent(button,
                                    MouseEvent.MOUSE_EXITED,
                                    System.currentTimeMillis(),
                                    0,
                                    10,
                                    10,
                                    1,
                                    false))
    assertEquals(ColorButton.Status.NORMAL, button.status)
  }
}
