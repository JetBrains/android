/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.LayoutTestCase
import java.awt.image.BufferedImage

class ColorConverterTest : LayoutTestCase() {

  fun testGeneralConvert() {
    val converter = ColorConverter(ColorBlindMode.NONE)
    val startImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE)
    val endImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE)

    assertTrue(converter.convert(startImg, endImg))
  }

  fun testWrongImageType() {
    val converter = ColorConverter(ColorBlindMode.NONE)
    val startImg = BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
    val endImg = BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)

    assertFalse(converter.convert(startImg, endImg))
  }
}
