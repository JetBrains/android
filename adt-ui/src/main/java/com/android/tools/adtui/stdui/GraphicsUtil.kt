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
package com.android.tools.adtui.stdui

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Set the color and the alpha channel from the specified color value.
 */
fun Graphics2D.setColorAndAlpha(color: Color) {
  this.color = color
  if (color.alpha == 255) {
    this.composite = AlphaComposite.SrcOver
  }
  else {
    this.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.alpha / 255.0f)
  }
}

fun Rectangle2D.Float.applyInset(inset: Float) {
  this.x += inset
  this.y += inset
  this.width -= 2 * inset
  this.height -= 2 * inset
}
