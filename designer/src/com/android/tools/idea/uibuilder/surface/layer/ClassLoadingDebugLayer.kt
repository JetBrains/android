/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.layer

import com.android.tools.adtui.stdui.setColorAndAlpha
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.surface.LAYER_FONT
import com.android.tools.idea.uibuilder.surface.drawMultilineString
import com.intellij.openapi.module.Module
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import org.jetbrains.android.uipreview.HATCHERY

private const val PROGRESS_HEIGHT = 20
private const val PROGRESS_WIDTH = 100

/**
 * Draws an overlay layer to display current state of the [ModuleClassLoaderHatchery] for the
 * [module]. Each Clutch in the hatchery is visually represented by a labelled column of progress
 * bars each of which displays the progress of class loading in each of the [ModuleClassLoader].
 */
class ClassLoadingDebugLayer(val module: Module) : Layer() {
  override fun paint(gc: Graphics2D) {
    val g = gc.create() as Graphics2D
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setColorAndAlpha(JBColor.BLUE)
    g.font = LAYER_FONT

    val clipBounds = g.clipBounds
    var startY = clipBounds.y + 20
    val startX = clipBounds.x + 20

    val hatchery = module.getUserData(HATCHERY)
    if (hatchery != null) {
      startY += gc.drawMultilineString("Preloading progress:", startX, startY)
      val stats = hatchery.getStats()
      if (stats.isEmpty()) {
        gc.drawMultilineString("No clutches", startX, startY)
      } else {
        var horShift = 0
        stats.forEach {
          var vertShift = gc.drawMultilineString(it.label, startX + horShift, startY)
          it.states.forEach { stat ->
            g.setColorAndAlpha(Color.GREEN)
            g.drawRect(startX + horShift, startY + vertShift, PROGRESS_WIDTH, PROGRESS_HEIGHT)
            g.fillRect(
              startX + horShift,
              startY + vertShift,
              PROGRESS_WIDTH * stat.progress / stat.toDo,
              PROGRESS_HEIGHT
            )
            vertShift += 2 * PROGRESS_HEIGHT
          }
          horShift += PROGRESS_WIDTH + PROGRESS_WIDTH / 2
        }
      }
    } else {
      gc.drawMultilineString("No hatchery available", startX, startY)
    }

    g.dispose()
  }
}
