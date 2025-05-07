/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout.option

import com.android.tools.idea.uibuilder.layout.positionable.TestPositionableContent
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals
import org.junit.Test

class FocusLayoutManagerTest {

  @Test
  fun measureEmpty() {
    val manager = GridLayoutManager()
    val positions = manager.measure(emptyList(), 100, 100)
    assertEmpty(positions.keys)
  }

  @Test
  fun measureSingle() {
    //     P - measured position
    //     r - resize hovering area
    //     ________________________________________________
    //     |                   45 ↕                        |
    //     |                P_____________                 |
    //     |  35            |     ↔30     |    25          |
    //     |  ↔             |             |    ↔           |
    //     |                | ↕ 30        |                |
    //     |                |_____________|<-->            |
    //     |                              ↕ r              |
    //     |                       25 ↕                    |
    //     -------------------------------------------------
    val manager = FocusLayoutManager()
    val content = TestPositionableContent(null, Dimension(30, 30))
    val positions = manager.measure(listOf(content), 100, 120)
    assertEquals(1, positions.size)
    assertEquals(content, positions.keys.single())
    assertEquals(Point(25, 25), positions.values.single())
  }
}
