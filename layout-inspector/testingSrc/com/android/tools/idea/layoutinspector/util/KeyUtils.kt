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
fun zoomIn() {
  val dispatcher = IdeEventQueue.getInstance().keyEventDispatcher
  dispatcher.dispatchKeyEvent(createZoomKeyEvent(pressed = true, zoomIn = true))
  dispatcher.dispatchKeyEvent(createZoomKeyEvent(pressed = false, zoomIn = true))
}

/** Press and release meta/ctrl - key via the IdeEventQueue key dispatcher */
fun zoomOut() {
  val dispatcher = IdeEventQueue.getInstance().keyEventDispatcher
  dispatcher.dispatchKeyEvent(createZoomKeyEvent(pressed = true, zoomIn = false))
  dispatcher.dispatchKeyEvent(createZoomKeyEvent(pressed = false, zoomIn = false))
}

private fun createZoomKeyEvent(pressed: Boolean, zoomIn: Boolean) =
  KeyEvent(
    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner,
    if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED,
    System.nanoTime(),
    if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK,
    if (zoomIn) KeyEvent.VK_ADD else KeyEvent.VK_MINUS,
    if (zoomIn) '+' else '-',
  )
