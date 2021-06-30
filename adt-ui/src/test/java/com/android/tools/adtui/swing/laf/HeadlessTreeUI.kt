/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing.laf

import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeMouse
import java.awt.HeadlessException
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicTreeUI

/**
 * A stubbed [BasicTreeUI] for use in headless unit tests, where some functionality is
 * removed to avoid making calls that would otherwise throw a [HeadlessException]. This will
 * allow you to interact with [JTree] components using [FakeMouse] and [FakeKeyboard].
 *
 * NOTE: Changing the UI of a component can subtly change its behavior! This class may need to be
 * updated in the future to add more functionality, so it more closely matches its parent class.
 */
class HeadlessTreeUI : BasicTreeUI() {
  override fun isToggleSelectionEvent(event: MouseEvent): Boolean =
    SwingUtilities.isLeftMouseButton(event) && isMenuShortcutKeyDown(event)

  private fun isMenuShortcutKeyDown(event: MouseEvent) =
    // Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx yields HeadlessException on Linux and Windows test machines.
    // Use InputEvent.CTRL_DOWN_MASK instead.
    (event.modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
}
