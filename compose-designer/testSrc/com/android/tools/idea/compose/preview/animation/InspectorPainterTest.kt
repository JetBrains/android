/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import javax.swing.JSlider
import kotlin.test.assertEquals

class InspectorPainterTest {

  @Test
  fun zeroWidth() {
    val slider = mock(JSlider::class.java)
    Mockito.`when`(slider.width).thenReturn(0)
    Mockito.`when`(slider.maximum).thenReturn(200)
    Mockito.`when`(slider.minimum).thenReturn(0)
    val result = InspectorPainter.Slider.getTickIncrement(slider, 100)
    assertEquals(200, result)
  }

  @Test
  fun maxIntMaximum() {
    val slider = mock(JSlider::class.java)
    Mockito.`when`(slider.width).thenReturn(300)
    Mockito.`when`(slider.maximum).thenReturn(Int.MAX_VALUE)
    Mockito.`when`(slider.minimum).thenReturn(0)
    val result = InspectorPainter.Slider.getTickIncrement(slider, 100)
    assertEquals(700_000_000, result)
  }

  @Test
  fun largeMaximum() {
    val slider = mock(JSlider::class.java)
    Mockito.`when`(slider.width).thenReturn(300)
    Mockito.`when`(slider.maximum).thenReturn(200_000_000)
    Mockito.`when`(slider.minimum).thenReturn(0)
    val result = InspectorPainter.Slider.getTickIncrement(slider, 100)
    assertEquals(60_000_000, result)
  }

  @Test
  fun smallMaximum() {
    val slider = mock(JSlider::class.java)
    Mockito.`when`(slider.width).thenReturn(300)
    Mockito.`when`(slider.maximum).thenReturn(5)
    Mockito.`when`(slider.minimum).thenReturn(0)
    val result = InspectorPainter.Slider.getTickIncrement(slider, 100)
    assertEquals(1, result)
  }
}