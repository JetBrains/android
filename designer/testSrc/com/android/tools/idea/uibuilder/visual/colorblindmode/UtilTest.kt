/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.colorblindmode

import junit.framework.TestCase

class UtilTest : TestCase() {

  fun testColor() {
    val color1 = 0xFF0000
    val red = r(color1)
    assertEquals(0xFF, red)

    val color2 = 0x00FF00
    val green = g(color2)
    assertEquals(0xFF, green)

    val color3 = 0x0000FF
    val blue = b(color3)
    assertEquals(0xFF, blue)

    val colorCombined = combine(red, green, blue)
    assertEquals(0xFFFFFF, colorCombined)
  }

  fun testMat3D() {
    val rgb = RGB(1.0, 2.0, 3.0)
    val mult: RGB = IDENTITY_MATRIX * rgb

    assertEquals(rgb, mult)
  }

  fun testMat3D2() {
    val mat3D1 = IDENTITY_MATRIX
    val mat3D2 = IDENTITY_MATRIX

    val mult: Mat3D = mat3D1 * mat3D2

    assertEquals(mat3D1, mult)
    assertEquals(mat3D2, mult)
  }

  fun testRgb2Lms() {
    val mult = RGB_TO_LMS * LMS_TO_RGB
    assertTrue(IDENTITY_MATRIX.close(mult))
  }
}
