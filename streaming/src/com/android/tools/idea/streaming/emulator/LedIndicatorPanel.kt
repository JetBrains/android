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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.LedIndicator as LedIndicatorMessage
import com.android.tools.idea.concurrency.createCoroutineScope
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/** A panel displaying the state of LED indicators of an Intelligent Eyeware AVD. */
internal class LedIndicatorPanel(emulator: EmulatorController, parentDisposable: Disposable) : JComponent() {

  private var ledStates = emptyMap<LedIndicatorMessage.Facing, Color?>()

  init {
    setToolTipText(HtmlChunk.empty())
    val coroutineScope = parentDisposable.createCoroutineScope()
    val notificationTracker = NotificationTracker.forEmulator(emulator)
    coroutineScope.launch(Dispatchers.EDT) {
      notificationTracker.ledStates.collect { states ->
        ledStates = states
        repaint()
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    val size = JBUIScale.scale(INDICATOR_SIZE)
    val spacing = JBUIScale.scale(INDICATOR_SPACING)
    val width = size + JBUIScale.scale(2)
    val height = LedIndicator.entries.size * size + (LedIndicator.entries.size - 1).coerceAtLeast(0) * spacing
    return Dimension(width, height)
  }

  override fun getMinimumSize(): Dimension = preferredSize

  override fun getMaximumSize(): Dimension = preferredSize

  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    val g = graphics.create() as Graphics2D
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val size = JBUIScale.scale(INDICATOR_SIZE)
    val spacing = JBUIScale.scale(INDICATOR_SPACING)
    val x = (width - size) / 2

    for (indicator in LedIndicator.entries) {
      val y = (1 - indicator.ordinal) * (size + spacing)
      val ledColor = ledStates[indicator.facing]?.rgb ?: 0

      g.color = JBColor.border()
      g.fillOval(x, y, size, size)
      @Suppress("UseJBColor")
      g.color = Color(combineWithBackground(ledColor, INDICATOR_BACKGROUND_COLOR.rgb))
      g.fillOval(
        x + INDICATOR_BORDER_WIDTH,
        y + INDICATOR_BORDER_WIDTH,
        size - INDICATOR_BORDER_WIDTH * 2,
        size - INDICATOR_BORDER_WIDTH * 2,
      )
    }
    g.dispose()
  }

  private fun combineWithBackground(foreground: Int, background: Int): Int {
    var result = 0
    for (offset in 0..16 step 8) {
      result += (255 - (255 - getChannel(foreground, offset)) * (255 - getChannel(background, offset)) / 255) shl offset
    }
    return result
  }

  private fun getChannel(rgb: Int, offset: Int): Int = (rgb shr offset) and 0xFF

  override fun getToolTipText(event: MouseEvent): String? {
    val size = JBUIScale.scale(INDICATOR_SIZE)
    val spacing = JBUIScale.scale(INDICATOR_SPACING)
    val x = (width - size) / 2
    if (event.x !in x..(x + size)) return null
    val y = event.y
    val i = 1 - y / (size + spacing)
    if (i in LedIndicator.entries.indices) {
      val yInRow = y % (size + spacing)
      if (yInRow <= size) {
        val indicator = LedIndicator.entries[i]
        return indicator.displayName
      }
    }
    return null
  }

  companion object {
    @VisibleForTesting const val INDICATOR_SIZE = 12
    @VisibleForTesting const val INDICATOR_BORDER_WIDTH = 3
    @VisibleForTesting const val INDICATOR_SPACING = 12
    private val INDICATOR_BACKGROUND_COLOR = JBColor(Gray._128, Gray._64)
  }

  private enum class LedIndicator(val facing: LedIndicatorMessage.Facing, val displayName: String) {
    INSIDE(LedIndicatorMessage.Facing.INSIDE, "Inside LED"),
    OUTSIDE(LedIndicatorMessage.Facing.OUTSIDE, "Outside LED"),
  }
}
