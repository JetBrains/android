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
package com.android.tools.idea.naveditor.property.inspector

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTextField

class PlaceholderTextField : JTextField() {
  var placeHolderText: String? = null

  public override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (super.getText().isNotEmpty() || placeHolderText == null) {
      return
    }

    val g2 = g as Graphics2D

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = super.getDisabledTextColor()

    val metrics = g2.fontMetrics
    val x = insets.left + margin.left
    val y = (height + insets.top - insets.bottom + margin.top - margin.bottom - metrics.height) / 2 + metrics.ascent

    g2.drawString(placeHolderText, x, y)
  }
}