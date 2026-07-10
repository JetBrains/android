/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering

import java.awt.Dimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConstrainedRenderSizeProviderTest {

  @Test
  fun testTargetSizeWithQuality() {
    val maxImageSize = 1000L * 1000L // 1,000,000 pixels
    var quality = 1.0f

    val provider = ConstrainedRenderSizeProvider(maxImageSize) { quality }

    // 1. With 1.0 quality and size within bounds, target size should be exactly requested size
    var targetSize = provider.getTargetSize(500, 500)
    assertNotNull(targetSize)
    assertEquals(Dimension(500, 500), targetSize)

    // 2. With 0.25 quality (multiplier 0.5), target size should be downscaled by 0.5
    quality = 0.25f
    targetSize = provider.getTargetSize(500, 500)
    assertNotNull(targetSize)
    assertEquals(Dimension(250, 250), targetSize)
  }

  @Test
  fun testTargetSizeWithMaxSizeConstraint() {
    val maxImageSize = 100L * 100L // 10,000 pixels
    val quality = 1.0f

    val provider = ConstrainedRenderSizeProvider(maxImageSize) { quality }

    // Size within bounds (50 * 50 = 2500 < 10000)
    var targetSize = provider.getTargetSize(50, 50)
    assertNotNull(targetSize)
    assertEquals(Dimension(50, 50), targetSize)

    // Size exceeding bounds (200 * 200 = 40000 > 10000)
    // Scale should be sqrt(10000 / 40000) = 0.5
    targetSize = provider.getTargetSize(200, 200)
    assertNotNull(targetSize)
    assertEquals(Dimension(100, 100), targetSize)
  }
}
