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

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TransitionTest {

  private val rect =
    AnimatedProperty.Builder()
      .add(100, ComposeUnit.Rect(0f, 0f, 0f, 0f))
      .add(200, ComposeUnit.Rect(1f, 1f, 1f, 1f))
      .build()

  private val offset =
    AnimatedProperty.Builder()
      .add(50, ComposeUnit.Offset(0f, 0f))
      .add(150, ComposeUnit.Offset(1f, 1f))
      .build()

  @Test
  fun `create transition`() {
    val transition = Transition(mutableMapOf(0 to rect, 1 to offset))
    assertEquals(50, transition.startMillis)
    assertEquals(200, transition.endMillis)
    assertEquals(150, transition.duration)
  }

  @Test
  fun `create transition with null`() {
    val transition = Transition(mutableMapOf(0 to rect, 1 to offset, 2 to null))
    assertEquals(50, transition.startMillis)
    assertEquals(200, transition.endMillis)
    assertEquals(150, transition.duration)
  }

  @Test
  fun `create transition with only nulls`() {
    val transition = Transition(mutableMapOf(0 to null, 1 to null, 2 to null))
    assertNull(transition.startMillis)
    assertNull(transition.endMillis)
    assertEquals(0, transition.duration)
  }

  @Test
  fun `create empty transition`() {
    val transition = Transition(mutableMapOf())
    assertNull(transition.startMillis)
    assertNull(transition.endMillis)
    assertEquals(0, transition.duration)
  }
}
