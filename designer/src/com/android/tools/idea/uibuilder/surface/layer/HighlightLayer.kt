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
package com.android.tools.idea.common.surface.layer

import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.layer.BorderPainter
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Rectangle
import org.jetbrains.annotations.NotNull

private val selectedBorderColor =
  JBColor.namedColor("ScreenView.selectedBorderColor", JBColor(0x3573f0, 0x548af7))

public class HighlightLayer
@JvmOverloads
constructor(@NotNull screenView: ScreenView, @NotNull private val surface: NlDesignSurface) :
  Layer() {
  private var rectanglesToHighlight = mutableListOf<Rectangle>()
  private val borderPainter =
    BorderPainter(JBUI.scale(2), selectedBorderColor, selectedBorderColor, useHighQuality = true)

  @Override
  override fun paint(gc: Graphics2D) {
    rectanglesToHighlight.forEach({ borderPainter.paint(gc, it.x, it.y, it.width, it.height) })
  }

  @Override
  override fun onHover(x: Int, y: Int) {
    rectanglesToHighlight = mutableListOf<Rectangle>()
  }

  /**
   * Called when we want to highlight a component
   *
   * @param x the lowest x coordinate of the component
   * @param y the lowest y coordinate of the component
   * @param width the width of the component
   * @param height the height of the component
   */
  fun highlight(x: Int, y: Int, width: Int, height: Int) {
    rectanglesToHighlight.add(Rectangle(x, y, width, height))
    surface.repaint()
  }

  /** Called when we want to remove any previous highlight */
  fun clear() {
    rectanglesToHighlight = mutableListOf<Rectangle>()
  }
}
