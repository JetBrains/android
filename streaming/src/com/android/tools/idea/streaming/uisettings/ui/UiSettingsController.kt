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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.tools.idea.streaming.uisettings.binding.ChangeListener

/**
 * A controller for the [UiSettingsPanel] that populates the model and reacts to changes to the model initiated by the UI.
 */
internal abstract class UiSettingsController(
  /**
   * The model that this controller is interacting with.
   */
  protected val model: UiSettingsModel
) {

  init {
    model.inDarkMode.uiChangeListener = ChangeListener(::setDarkMode)
  }

  /**
   * Populate all settings in the model.
   */
  abstract suspend fun populateModel()

  /**
   * Changes the dark mode on the device/emulator.
   */
  protected abstract fun setDarkMode(on: Boolean)
}
