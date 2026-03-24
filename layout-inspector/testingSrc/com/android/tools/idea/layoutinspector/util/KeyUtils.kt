/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.util

import com.android.tools.adtui.swing.FakeUi
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.util.SystemInfo
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/** Press and release the <TAB> key via FakeUi keyboard */
fun FakeUi.tab() = keyboard.pressAndRelease(KeyEvent.VK_TAB)

/** Press and release meta/ctrl + key via the IdeEventQueue key dispatcher */
fun pressAndReleaseCtrlPlus() {
  val dispatcher = IdeEventQueue.getInstance().keyEventDispatcher
  dispatcher.dispatchKeyEvent(createKeyEvent(pressed = true, KeyEvent.VK_ADD, '+'))
  dispatcher.dispatchKeyEvent(createKeyEvent(pressed = false, KeyEvent.VK_ADD, '+'))
}

/** Press and release meta/ctrl - key via the IdeEventQueue key dispatcher */
fun pressAndReleaseCtrlMinus() {
  val dispatcher = IdeEventQueue.getInstance().keyEventDispatcher
  dispatcher.dispatchKeyEvent(createKeyEvent(pressed = true, KeyEvent.VK_MINUS, '-'))
  dispatcher.dispatchKeyEvent(createKeyEvent(pressed = false, KeyEvent.VK_MINUS, '-'))
}

private fun createKeyEvent(pressed: Boolean, keyCode: Int, char: Char) =
  KeyEvent(
    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner,
    if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED,
    System.nanoTime(),
    if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK,
    keyCode,
    char,
  )
