/*
 * Copyright (C) 2023 The Android Open Source Project
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
package main.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object UnitConversion {
  /**
   * Converts an integer value representing pixels to density-independent pixels (Dp),
   * taking into account the current display density.
   *
   * @return The equivalent value in density-independent pixels (Dp).
   */
  @Composable
  fun Int.toDpWithCurrentDisplayDensity(): Dp {
    // Extract the pixel value from the receiver.
    val pxValue = this

    // Retrieve the current display density from the ambient composition.
    val currentDisplayDensity = LocalDensity.current

    // Extract the density value from the Density instance.
    val densityValue = currentDisplayDensity.density

    // Convert the pixel value to density-independent pixels (Dp),
    // taking into account the current display density.
    return ((with(currentDisplayDensity) { pxValue.toDp() }).value * densityValue).dp
  }
}