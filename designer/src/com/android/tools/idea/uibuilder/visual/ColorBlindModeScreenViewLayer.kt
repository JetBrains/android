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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorLut
import com.android.tools.idea.uibuilder.visual.colorblindmode.buildColorLut
import com.android.tools.idea.uibuilder.visual.colorblindmode.convert

private const val DIM = 16

/**
 * Screen view layer that can override the results from the layoutlib to simulate different
 * color blind modes.
 *
 * @param mode enum that represents the different color blind mode to simulate. Null if the view layer is displaying the original image.
 */
class ColorBlindModeScreenViewLayer(screenView: ScreenView, val mode: ColorBlindMode?) :
  ScreenViewLayer(screenView) {

  private var lut: ColorLut? = null

  override fun setLastRenderResult(result: RenderResult?) {
    super.setLastRenderResult(result)

    if (mode == null) {
      // Displaying the original image. No need to apply the simulation.
      return
    }

    if (lut == null) {
      lut = buildColorLut(DIM, mode)
    }

    val original = result?.renderedImage ?: return
    val copied = original.copy ?: return

    convert(copied, copied, mode, lut)

    original.paint{ g2D ->
      val w = original.width
      val h = original.height
      g2D.drawImage(copied, 0, 0, w, h, 0, 0, w,  h, null)
    }
  }
}