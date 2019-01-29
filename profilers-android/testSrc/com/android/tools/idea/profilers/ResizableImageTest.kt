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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB

class ResizableImageTest {

  @Test
  fun preferredSizeMatchesImageSizeByDefaultAndCanBeOverridden() {
    val image = BufferedImage(123, 456, TYPE_INT_ARGB)
    val resizableImage = ResizableImage(image)

    assertThat(resizableImage.preferredSize).isEqualTo(Dimension(123, 456))

    resizableImage.preferredSize = Dimension(48, 48)
    assertThat(resizableImage.preferredSize).isEqualTo(Dimension(48, 48))

    resizableImage.preferredSize = null // Setting to "null" signifies reset to default
    assertThat(resizableImage.preferredSize!!).isEqualTo(Dimension(123, 456))
  }
}