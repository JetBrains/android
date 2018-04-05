/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import java.awt.Color

/**
 * Interface for implementing the color pipette.
 * @see ColorPipetteButton
 * @see GraphicalColorPipette
 */
interface ColorPipette {

  fun pick(callback: Callback)

  interface Callback {

    /**
     * Called when the color is picked.
     */
    fun picked(pickedColor: Color)

    /**
     * Called when hovered color is changed but not really be picked.<br>
     * [updatedColor] is the color of current hovered pixel.
     */
    fun update(updatedColor: Color) = Unit

    /**
     * Called when the picking is canceled.
     */
    fun cancel() = Unit
  }
}
