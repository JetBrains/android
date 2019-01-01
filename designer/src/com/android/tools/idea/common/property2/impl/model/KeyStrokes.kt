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
package com.android.tools.idea.common.property2.impl.model

import com.intellij.openapi.util.SystemInfo
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

object KeyStrokes {
  private val CMD_KEY_MASK = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK

  internal val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  internal val escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
  internal val tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
  internal val backtab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
  internal val space = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)
  internal val f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)
  internal val shiftF1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK)
  internal val browse = KeyStroke.getKeyStroke(KeyEvent.VK_B, CMD_KEY_MASK)
}
