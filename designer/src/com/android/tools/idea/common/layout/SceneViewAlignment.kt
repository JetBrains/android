/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.layout

import com.android.tools.idea.common.surface.SceneView
import java.awt.Component.CENTER_ALIGNMENT
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Component.RIGHT_ALIGNMENT

/**
 * Alignment for the [SceneView] when its size is less than the minimum size. If the size of the
 * [SceneView] is less than the minimum, this enum describes how to align the content within the
 * rectangle formed by the minimum size.
 *
 * @param alignmentX The Swing alignment value equivalent to this alignment setting. See
 *   [LEFT_ALIGNMENT], [RIGHT_ALIGNMENT] and [CENTER_ALIGNMENT].
 */
enum class SceneViewAlignment(val alignmentX: Float) {
  /** Align content to the left within the minimum size bounds. */
  LEFT(LEFT_ALIGNMENT),

  /** Align content to the right within the minimum size bounds. */
  RIGHT(RIGHT_ALIGNMENT),

  /** Center contents within the minimum size bounds. */
  CENTER(CENTER_ALIGNMENT),
}
