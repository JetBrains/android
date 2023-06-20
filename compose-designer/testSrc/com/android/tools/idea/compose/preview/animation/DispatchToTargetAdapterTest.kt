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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.swing.FakeUi
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class DispatchToTargetAdapterTest {
  @Test
  fun `events are dispatched to target`() {
    val target =
      JPanel().apply {
        setSize(100, 100)
        location = Point(25, 25)
        addMouseListener(
          object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
              assertNotNull(e)
              assertEquals(Point(25, 25), e.point)
            }

            override fun mousePressed(e: MouseEvent?) {
              assertNotNull(e)
              assertEquals(Point(25, 25), e.point)
            }

            override fun mouseReleased(e: MouseEvent?) {
              assertNotNull(e)
              assertEquals(Point(25, 25), e.point)
            }
          }
        )
      }
    val panel =
      JPanel().apply {
        setSize(100, 100)
        addMouseListener(DispatchToTargetAdapter(target))
      }
    val ui = FakeUi(panel)
    ui.mouse.click(50, 50)
    ui.mouse.press(50, 50)
    ui.mouse.release()
  }
}
