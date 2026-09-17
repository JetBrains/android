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
package com.android.tools.idea.configurations

import com.android.tools.configurations.Configuration.ImageTransformationType
import com.android.tools.configurations.SystemUiPreferences
import com.google.common.truth.Truth.assertThat
import java.awt.image.BufferedImage
import java.util.function.Consumer
import org.junit.Test

class SystemUiPreferencesTest {
  @Test
  fun testFontScale() {
    val prefs = SystemUiPreferences()
    assertThat(prefs.fontScale).isWithin(0.0f).of(1f)

    prefs.fontScale = 1.5f
    assertThat(prefs.fontScale).isWithin(0.0f).of(1.5f)
  }

  @Test
  fun testImageTransformationsExhaustive() {
    val prefs = SystemUiPreferences()
    assertThat(prefs.imageTransformation).isNull()

    var colorBlindInvoked = false
    val colorBlindConsumer = Consumer { image: BufferedImage? -> colorBlindInvoked = true }

    // 1. Add first transformation
    prefs.setImageTransformation(ImageTransformationType.COLOR_BLIND_MODE, colorBlindConsumer)
    assertThat(prefs.imageTransformation).isNotNull()
    requireNotNull(prefs.imageTransformation).accept(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    assertThat(colorBlindInvoked).isTrue()

    // 2. Add second transformation
    var glassesInvoked = false
    val glassesConsumer = Consumer { image: BufferedImage? -> glassesInvoked = true }
    prefs.setImageTransformation(ImageTransformationType.GLASSES_BACKGROUND_IMAGE, glassesConsumer)

    colorBlindInvoked = false
    requireNotNull(prefs.imageTransformation).accept(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    assertThat(colorBlindInvoked).isTrue()
    assertThat(glassesInvoked).isTrue()

    // 3. Remove first transformation
    prefs.setImageTransformation(ImageTransformationType.COLOR_BLIND_MODE, null)
    colorBlindInvoked = false
    glassesInvoked = false
    requireNotNull(prefs.imageTransformation).accept(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    assertThat(colorBlindInvoked).isFalse()
    assertThat(glassesInvoked).isTrue()

    // 4. Remove second transformation
    prefs.setImageTransformation(ImageTransformationType.GLASSES_BACKGROUND_IMAGE, null)
    assertThat(prefs.imageTransformation).isNull()
  }

  @Test
  fun testCopyFromClonesStateCorrectly() {
    val original = SystemUiPreferences()
    original.fontScale = 2.0f
    original.isEdgeToEdge = false
    original.isGestureNav = false
    original.useThemedIcon = true

    val clone = SystemUiPreferences()
    clone.copyFrom(original)

    assertThat(clone.fontScale).isWithin(0.0f).of(2.0f)
    assertThat(clone.isEdgeToEdge).isFalse()
    assertThat(clone.isGestureNav).isFalse()
    assertThat(clone.useThemedIcon).isTrue()
  }
}
