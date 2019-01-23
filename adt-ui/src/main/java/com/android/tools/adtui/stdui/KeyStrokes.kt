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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.common.AdtUiUtils
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

object KeyStrokes {
  val enter: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  val escape: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
  val tab: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
  val backtab: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
  val space: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)
  val ctrlSpace: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK)
  val f1: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)
  val shiftF1: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK)
  val cmdBrowse: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_B, AdtUiUtils.getActionMask())
  val cmdFind: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, AdtUiUtils.getActionMask())
  val cmdHome: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, AdtUiUtils.getActionMask())
  val cmdEnd: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, AdtUiUtils.getActionMask())
  val cmdMinus: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, AdtUiUtils.getActionMask())
  val left: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)
  val right: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)
  val down: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
  val up: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
  val pageDown: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0)
  val pageUp: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0)
  val altDown: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK)
}
