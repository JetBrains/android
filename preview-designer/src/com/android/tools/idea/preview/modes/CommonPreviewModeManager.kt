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
package com.android.tools.idea.preview.modes

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val log = Logger.getInstance(CommonPreviewModeManager::class.java)

/**
 * Common implementation of a [PreviewModeManager].
 *
 * @param onEnter a function that will be called with the new mode when switching to a new mode.
 * @param onExit a function that will be called with the current mode when switching to a new mode.
 */
class CommonPreviewModeManager : PreviewModeManager {

  private val _mode = MutableStateFlow<PreviewMode>(PreviewMode.Default)
  override val mode = _mode.asStateFlow()
  private val lock = Any()

  /**
   * When entering one of the [PreviewMode.Focus] modes (interactive, animation, etc.), the previous
   * mode is saved into [restoreMode]. After exiting the special mode [restoreMode] is set.
   *
   * TODO(b/293257529) Need to restore selected tab as well in Gallery mode.
   */
  private var restoreMode: PreviewMode? = null

  override fun restorePrevious() {
    restoreMode?.let { _mode.value = it }
  }

  override fun setMode(mode: PreviewMode) {
    synchronized(lock) {
      restoreMode = this._mode.value
      this._mode.value = mode
    }
  }
}
