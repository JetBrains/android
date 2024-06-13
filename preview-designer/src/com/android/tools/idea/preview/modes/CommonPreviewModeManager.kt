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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.android.uipreview.AndroidEditorSettings

/** Common implementation of a [PreviewModeManager]. */
class CommonPreviewModeManager : PreviewModeManager {

  private val _mode = MutableStateFlow<PreviewMode>(getStoredDefaultValue())
  override val mode = _mode.asStateFlow()
  private val lock = Any()

  private fun getStoredDefaultValue() =
    when (AndroidEditorSettings.getInstance().globalState.preferredPreviewLayoutMode) {
      AndroidEditorSettings.LayoutType.LIST -> PreviewMode.Default(LIST_LAYOUT_OPTION)
      AndroidEditorSettings.LayoutType.GRID -> PreviewMode.Default(GRID_LAYOUT_OPTION)
      AndroidEditorSettings.LayoutType.GALLERY -> PreviewMode.Gallery(null)
      else -> PreviewMode.Default()
    }

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
      val currentMode = this._mode.value
      // We only change the restore mode when we change mode type. That way we can go back
      // to the latest state of the previous type of mode.
      if (PreviewModeManager.areModesOfDifferentType(currentMode, mode)) {
        // Only set the restore mode for modes that can be used as such. Otherwise, set the restore
        // mode to Default.
        restoreMode =
          if (currentMode is PreviewMode.RestorePreviewMode) currentMode else PreviewMode.Default()
      }
      this._mode.value = mode
    }
  }
}
