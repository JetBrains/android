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
package com.android.tools.idea.uibuilder.surface

import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics2D

val LAYER_FONT = JBUI.Fonts.create(Font.MONOSPACED, 9)

fun Graphics2D.drawMultilineString(lines: String, x: Int, y: Int): Int {
  var lineStart = 0
  val fontMetrics = this.fontMetrics
  lines.split('\n').forEach {
    this.drawString(it, x, y + lineStart)
    lineStart += fontMetrics.height
  }

  return lineStart
}
