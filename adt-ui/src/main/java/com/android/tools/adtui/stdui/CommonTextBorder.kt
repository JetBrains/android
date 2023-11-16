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
package com.android.tools.adtui.stdui

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets

val HIDE_RIGHT_BORDER = Key.create<Boolean>("HideRightBorder")

/**
 * Border for showing outlines in a JTextField with the capability of hiding the right border.
 *
 * This version is a workaround for supporting a TableExpandableItemsHandler.
 */
class CommonTextBorder : DarculaTextBorder() {

  override fun getBorderInsets(c: Component): Insets {
    val insets = super.getBorderInsets(c)
    if (ClientProperty.isTrue(c, HIDE_RIGHT_BORDER)) {
      // Hide the border on the right to allow for proper painting of the item in the table with a popup on the right.
      insets.right = 0
    }
    return insets
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    // Paint the right edge of the border outside of the clipping region (hack to avoid the display of the right border).
    val extraWidth = if (ClientProperty.isTrue(c, HIDE_RIGHT_BORDER)) JBUIScale.scale(3) else 0
    super.paintBorder(c, g, x, y, width + extraWidth, height)
  }
}