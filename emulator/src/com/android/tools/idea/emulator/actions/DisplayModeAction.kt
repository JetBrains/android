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
package com.android.tools.idea.emulator.actions

import com.android.emulator.control.DisplayModeValue
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Sets a display mode for a resizable AVD.
 */
internal sealed class DisplayModeAction(val mode: DisplayModeValue) : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    if (getCurrentDisplayMode(event) != mode) {
      val emulator = getEmulatorController(event) ?: return
      emulator.setDisplayMode(mode)
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = hasDisplayModes(event) && isEmulatorConnected(event)
  }

  class Desktop : DisplayModeAction(DisplayModeValue.DESKTOP)

  class Foldable : DisplayModeAction(DisplayModeValue.FOLDABLE)

  class Phone : DisplayModeAction(DisplayModeValue.PHONE)

  class Tablet : DisplayModeAction(DisplayModeValue.TABLET)
}

internal fun hasDisplayModes(event: AnActionEvent): Boolean =
  getEmulatorController(event)?.emulatorConfig?.displayModes?.isNotEmpty() ?: false

internal fun getCurrentDisplayMode(event: AnActionEvent) =
  getEmulatorView(event)?.displayMode?.displayModeId ?: DisplayModeValue.UNRECOGNIZED
