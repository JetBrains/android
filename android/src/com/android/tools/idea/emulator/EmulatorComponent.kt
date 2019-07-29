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
package com.android.tools.idea.emulator

import java.awt.Color
import java.awt.Graphics
import javax.swing.JComponent

// TODO: Add tabs to support multiple running emulators simultaneously
class EmulatorComponent : JComponent() {
  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    // TODO: Paint emulator here
    g.color = Color.RED
    g.fillRect(0, 0, width, height)

    g.color = Color.BLUE
    g.fillRect(100, 100, width - 200, height - 200)
  }
}