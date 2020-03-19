/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage

/**
 * Device frame and mask layout for particular display dimensions and rotation.
 *
 * @param skinSize the dimensions of the layout
 * @param displayRect the display rectangle
 * @param background the image of the device frame if any
 * @param mask the device display mask if any
 */
internal class ScaledSkinLayout(
  val skinSize: Dimension,
  val displayRect: Rectangle,
  val background: AnchoredImage?,
  val mask: AnchoredImage?
) {
  /**
   * Creates a layout with no skin for the given dimensions.
   */
  constructor(skinSize: Dimension) :
    this(skinSize, Rectangle(0, 0, skinSize.width, skinSize.height), null, null)

  data class AnchoredImage(val image: BufferedImage, val x: Int, val y: Int)
}