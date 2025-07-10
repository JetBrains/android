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
package com.android.tools.idea.compose.preview.actions.glasses

import java.awt.image.BufferedImage
import java.util.function.Consumer

enum class GlassesBackground(val displayName: String, val fileName: String?) {
  /** No background is applied. */
  NONE("No Background", null),
  LIGHT_BACKGROUND("Light Background", "light_bg.png"),
  DARK_BACKGROUND("Dark Background", "dark_bg.png"),
  GRADIENT_BACKGROUND("Gradient Background", "gradient_bg.png");

  val imageTransform: Consumer<BufferedImage> =
    Consumer<BufferedImage> {
      GlassesBackgroundBlendMode.getInstance(this@GlassesBackground)?.applyBackground(it)
    }
}
