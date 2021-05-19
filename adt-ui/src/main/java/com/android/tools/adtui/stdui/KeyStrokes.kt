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
  val ENTER: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  val ESCAPE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
  val TAB: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
  val BACKTAB: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
  val TYPED_SPACE: KeyStroke = KeyStroke.getKeyStroke(' ')
  val SPACE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)
  val CTRL_SPACE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK)
  val F1: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)
  val SHIFT_F1: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK)
  val CMD_HOME: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_HOME, AdtUiUtils.getActionMask())
  val CMD_END: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_END, AdtUiUtils.getActionMask())
  val CMD_MINUS: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, AdtUiUtils.getActionMask())
  val HOME: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0)
  val END: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_END, 0)
  val LEFT: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)
  val RIGHT: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)
  val NUM_LEFT: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0)
  val NUM_RIGHT: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0)
  val DOWN: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
  val UP: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)
  val PAGE_DOWN: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0)
  val PAGE_UP: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0)
  val ALT_DOWN: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK)
}
