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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.LedIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.ui.JBColor.border
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

  private val ledIndicators = emulator.emulatorConfig.ledIndicators
  private var ledStates = emptyMap<Int, Color?>()

  init {
    val coroutineScope = parentDisposable.createCoroutineScope()
    val notificationReceiver = NotificationReceiver.forEmulator(emulator)
    coroutineScope.launch(Dispatchers.EDT) {
      notificationReceiver.ledStates.collect { states ->
        ledStates = states
        repaint()
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    val size = JBUIScale.scale(INDICATOR_SIZE)
    val spacing = JBUIScale.scale(INDICATOR_SPACING)
    val width = size + JBUIScale.scale(2)
    val height = ledIndicators.size * size + (ledIndicators.size - 1).coerceAtLeast(0) * spacing
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

    for (i in ledIndicators.indices) {
      val indicator = ledIndicators[i]
      val y = i * (size + spacing)
      val color = ledStates[indicator.id]

      if (color != null) {
        g.color = color
        g.fillOval(x, y, size, size)
      }
      g.color = border()
      g.drawOval(x, y, size, size)
    }
    g.dispose()
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val size = JBUIScale.scale(INDICATOR_SIZE)
    val spacing = JBUIScale.scale(INDICATOR_SPACING)
    val x = (width - size) / 2
    if (event.x !in x..(x + size)) return null
    val y = event.y
    val i = y / (size + spacing)
    if (i in ledIndicators.indices) {
      val yInRow = y % (size + spacing)
      if (yInRow <= size) {
        val indicator = ledIndicators[i]
        return when (indicator.facing) {
          LedIndicator.Facing.INSIDE -> "Inside LED"
          else -> "Outside LED"
        }
      }
    }
    return null
  }

  companion object {
    @VisibleForTesting const val INDICATOR_SIZE = 8
    @VisibleForTesting const val INDICATOR_SPACING = 12
  }
}
