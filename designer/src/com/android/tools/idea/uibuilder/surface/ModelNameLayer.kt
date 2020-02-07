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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.surface.Layer
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.JBColor
import java.awt.FontMetrics
import java.awt.Graphics2D

/**
 * The text appended at the tail of trimmed label. For example,: assuming all the characters have same width, when there is only 10
 * characters can be displayed, the text "Android Studio" is display as "Android S...".
 * In the implementation, the width of characters are measured by the [FontMetrics] of the using [java.awt.Font] of [Graphics2D].
 */
@VisibleForTesting
const val TRIMMED_TAIL = "..."

internal class ModelNameLayer(private val myScreenView: ScreenViewBase) : Layer() {
  override fun paint(originalGraphics: Graphics2D) {
    val modelName = myScreenView.sceneManager.model.modelDisplayName ?: return
    val g2d = originalGraphics.create() as Graphics2D
    try {
      g2d.color = JBColor.foreground()

      val font = myScreenView.labelFont
      val metrics = g2d.getFontMetrics(font)
      val fontHeight = metrics.height

      val x = myScreenView.x
      val y = myScreenView.y - (myScreenView.margin.top - fontHeight)

      val textToDisplay = createTrimmedText(modelName, myScreenView.size.width) { metrics.stringWidth(it) }

      g2d.setRenderingHints(HQ_RENDERING_HINTS)
      g2d.drawString(textToDisplay, x, y)
    }
    finally {
      g2d.dispose()
    }
  }
}

/**
 * Create the trimmed text for the given font metrics. If there is enough space to display [label] then it does nothing, otherwise it
 * replace tail text with [TRIMMED_TAIL] to fit the width of [availableWidth].
 */
@VisibleForTesting
fun createTrimmedText(label: String, availableWidth: Int, textMeasurer: (String) -> Int): String {
  if (textMeasurer(label) <= availableWidth) {
    // Have enough space, do not need to trim.
    return label
  }

  var charsWidth = textMeasurer(TRIMMED_TAIL)
  for (index in label.indices) {
    val charAtIndex = label[index]
    charsWidth += textMeasurer(charAtIndex.toString())
    if (charsWidth > availableWidth) {
      // After adding charAtIndex the length is out of bound. Create a substring with range [0, index).
      // It is possible that there is no even a space for fist character, in such case we force return tail string.
      return label.substring(0, maxOf(0, index)) + TRIMMED_TAIL
    }
  }
  // This happens when there is even no space for empty string. Logically this should never happen.
  return ""
}
