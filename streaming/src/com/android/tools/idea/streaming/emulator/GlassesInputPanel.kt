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
package com.android.tools.idea.streaming.emulator

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBBox
import com.intellij.util.ui.JBUI.Borders
import javax.swing.BoxLayout

/** The panel containing controls specific to AI glasses. */
class GlassesInputPanel(private val emulator: EmulatorController) : JBBox(BoxLayout.X_AXIS) {

  private val emulatorConfig
    get() = emulator.emulatorConfig

  init {
    border = Borders.compound(Borders.customLineBottom(JBColor.border()), Borders.empty(5, 10))
    val touchpadSize = emulatorConfig.touchpadSize
    if (touchpadSize != null) {
      val touchpadPanel = TouchpadPanel(emulator, touchpadSize)
      add(touchpadPanel)
      add(createHorizontalGlue())
    }
  }
}