/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.intellij.openapi.Disposable
import java.awt.Graphics2D
import javax.swing.JComponent

/**
 * A layer can be thought of as a very lightweight [JComponent] that is stacked on top of a [DesignSurface]. The critical difference
 * between using a [Layer] and a nested [JComponent] is that the layer does not have its own coordinate system and crucially, its own
 * clipping shape.
 */
abstract class Layer : Disposable {

  /**
   * Returns whether the overlay is hidden
   *
   * @return true if the selection overlay is hidden
   */
  open val isVisible: Boolean
    get() = true

  /**
   * Releases resources held by the overlay. Called by the editor when an overlay has been removed.
   */
  override fun dispose() {}

  /**
   * Paints the overlay.
   *
   * @param gc The Graphics object to draw into
   */
  abstract fun paint(gc: Graphics2D)

  /**
   * Called when this [Layer] is hovered.
   *
   * @param x x coordinate of the mouse on the [DesignSurface]
   * @param y y cordinate of the mouse on the [DesignSurface]
   */
  open fun onHover(@SwingCoordinate x: Int, @SwingCoordinate y: Int) {}
}
